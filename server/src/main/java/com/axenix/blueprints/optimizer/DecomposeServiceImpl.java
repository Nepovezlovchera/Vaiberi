package com.axenix.blueprints.optimizer;

import com.axenix.blueprints.model.Dto.DecomposeRequest;
import com.axenix.blueprints.model.Dto.DecomposeResponse;
import com.axenix.blueprints.model.Dto.ProjectFile;
import com.axenix.blueprints.model.Services.DecomposeService;
import com.axenix.blueprints.parser.ProjectModel;
import com.axenix.blueprints.parser.CompileErrorException;
import com.axenix.blueprints.sandbox.CompilerService;
import com.axenix.blueprints.sandbox.RunServiceImpl;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Детерминированная декомпозиция монолита (спецификация 6.2):
 * 1. статические вложенные классы выносятся в отдельные файлы (Order.java, Stats.java, ...);
 * 2. статические методы раскладываются по классам ответственности Parser / Analyzer / Optimizer
 *    (по смыслу или по явному плану); вызовы переписываются на Класс.метод, main остаётся в исходном классе.
 * Результат проверяется компиляцией; поведение должен подтвердить Quality Gate (/api/verify).
 */
public class DecomposeServiceImpl implements DecomposeService {

    private static final Pattern OPTIMIZED = Pattern.compile("\\b(HashMap|HashSet|TreeMap|LinkedHashMap|merge|computeIfAbsent)\\b");
    private static final Pattern PARSER = Pattern.compile("(?i)^(parse|read|scan|input|load|tokenize)");
    private static final Pattern ANALYZER = Pattern.compile("(?i)(analy|find|total|count|categor|valid|check|filter|stat|sum|unique|dup|compare|calc)");

    private final CompilerService compiler;

    public DecomposeServiceImpl(CompilerService compiler) {
        this.compiler = compiler;
    }

    @Override
    public DecomposeResponse decompose(DecomposeRequest req) {
        ProjectModel pm = ProjectModel.parse(req.files());
        boolean extract = req.extractClasses() == null || req.extractClasses();
        boolean split = req.splitMethods() == null || req.splitMethods();
        Set<String> taken = new HashSet<>(pm.units.keySet());
        List<ProjectFile> created = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        for (var entry : pm.units.entrySet()) {
            String path = entry.getKey();
            CompilationUnit cu = entry.getValue();
            String dir = path.contains("/") ? path.substring(0, path.lastIndexOf('/') + 1) : "";
            if (extract) extractNested(pm, path, cu, dir, taken, created, notes);
        }
        Set<String> extractedTypes = new HashSet<>();
        for (ProjectFile file : created) {
            if (!file.path().contains("/") && file.path().endsWith(".java"))
                extractedTypes.add(file.path().substring(0, file.path().length() - 5));
        }
        for (var unit : new ArrayList<>(pm.units.entrySet())) {
            String source = unit.getValue().toString();
            String rewritten = source;
            for (String type : extractedTypes)
                rewritten = rewritten.replaceAll("\\b[A-Za-z_$][A-Za-z0-9_$]*\\." + Pattern.quote(type) + "\\b", type);
            if (!rewritten.equals(source)) {
                pm.units.put(unit.getKey(), ProjectModel.parseUnit(ProjectModel.newParser(), unit.getKey(), rewritten));
                pm.touch(unit.getKey());
            }
        }


        if (split) {
            for (var entry : new ArrayList<>(pm.units.entrySet())) {
                String path = entry.getKey();
                String dir = path.contains("/") ? path.substring(0, path.lastIndexOf('/') + 1) : "";
                splitMethods(pm, path, entry.getValue(), dir, req.plan(), taken, created, notes);
            }
        }
        if (created.isEmpty()) {
            return new DecomposeResponse(req.files(), List.of(), "Нечего выносить: подходящих классов и методов не найдено."
                    + (notes.isEmpty() ? "" : " " + String.join("; ", notes)));
        }
        // Rebuild tokens from the final AST: lexical printing can retain an obsolete type scope.
        for (var unit : new ArrayList<>(pm.units.entrySet())) {
            pm.units.put(unit.getKey(), ProjectModel.newParser().parse(unit.getValue().toString()).getResult().orElseThrow());
        }
        List<ProjectFile> all = new ArrayList<>(pm.render());
        all.addAll(created);
        return new DecomposeResponse(all, created.stream().map(ProjectFile::path).toList(),
                String.join("; ", notes) + ". " + compileNote(all));
    }

    // ---------- 1. вложенные классы ----------

    private void extractNested(ProjectModel pm, String path, CompilationUnit cu, String dir, Set<String> taken,
                               List<ProjectFile> created, List<String> notes) {
        for (TypeDeclaration<?> outer : new ArrayList<>(cu.getTypes())) {
            for (BodyDeclaration<?> member : new ArrayList<>(outer.getMembers())) {
                if (!(member instanceof ClassOrInterfaceDeclaration nested)) continue;
                if (!nested.isStatic() && !nested.isInterface()) continue; // inner-классы зависят от экземпляра внешнего
                Set<String> outerFields = new HashSet<>();
                outer.getFields().forEach(field -> field.getVariables().forEach(v -> outerFields.add(v.getNameAsString())));
                if (nested.findAll(NameExpr.class).stream().anyMatch(n -> outerFields.contains(n.getNameAsString()))) {
                    notes.add("пропущен " + nested.getNameAsString() + ": зависит от поля внешнего класса");
                    continue;
                }
                String newPath = dir + nested.getNameAsString() + ".java";
                if (!taken.add(newPath)) {
                    notes.add("пропущен " + nested.getNameAsString() + ": файл " + newPath + " уже существует");
                    continue;
                }
                String outerName = outer.getNameAsString();
                String nestedName = nested.getNameAsString();
                for (var unit : new ArrayList<>(pm.units.entrySet())) {
                    if (!unit.getValue().getPackageDeclaration().map(Object::toString)
                            .equals(cu.getPackageDeclaration().map(Object::toString))) continue;
                    for (ClassOrInterfaceType reference : unit.getValue().findAll(ClassOrInterfaceType.class)) {
                        if (reference.getNameAsString().equals(nestedName)
                                && reference.getScope().filter(s -> s.toString().equals(outerName)).isPresent()) {
                            reference.removeScope();
                            pm.touch(unit.getKey());
                        }
                    }
                }
                created.add(new ProjectFile(newPath, extract(cu, nested)));
                nested.remove();
                pm.touch(path);
                notes.add("вынесен класс " + nested.getNameAsString() + " → " + newPath);
            }
        }
    }

    /** Новый файл: package и импорты исходного, класс без static/private/protected, члены доступны в пакете. */
    private static String extract(CompilationUnit src, ClassOrInterfaceDeclaration nested) {
        CompilationUnit out = new CompilationUnit();
        src.getPackageDeclaration().ifPresent(p -> out.setPackageDeclaration(p.clone()));
        src.getImports().forEach(i -> out.addImport(i.clone()));
        ClassOrInterfaceDeclaration copy = nested.clone();
        copy.setModifier(Modifier.Keyword.STATIC, false);
        copy.setModifier(Modifier.Keyword.PRIVATE, false);
        copy.setModifier(Modifier.Keyword.PROTECTED, false);
        for (BodyDeclaration<?> m : copy.getMembers()) {
            if (m instanceof NodeWithModifiers<?> nm) nm.setModifier(Modifier.Keyword.PRIVATE, false);
        }
        out.addType(copy);
        return out.toString();
    }

    // ---------- 2. методы по ответственности ----------

    private void splitMethods(ProjectModel pm, String path, CompilationUnit cu, String dir,
                              Map<String, List<String>> plan, Set<String> taken, List<ProjectFile> created, List<String> notes) {
        for (TypeDeclaration<?> type : new ArrayList<>(cu.getTypes())) {
            if (!(type instanceof ClassOrInterfaceDeclaration outer) || outer.isInterface()) continue;
            List<MethodDeclaration> statics = new ArrayList<>();
            for (MethodDeclaration md : outer.getMethods()) {
                if (md.isStatic() && md.getBody().isPresent() && !md.getNameAsString().equals("main")) statics.add(md);
            }
            // имя метода → класс назначения (все перегрузки вместе)
            Map<String, String> dest = new LinkedHashMap<>();
            if (plan != null && !plan.isEmpty()) {
                for (var e : plan.entrySet()) {
                    for (String name : e.getValue()) {
                        if (statics.stream().anyMatch(m -> m.getNameAsString().equals(name))) dest.putIfAbsent(name, e.getKey());
                    }
                }
            } else {
                for (MethodDeclaration md : statics) {
                    String cls = classify(md);
                    if (cls != null) dest.putIfAbsent(md.getNameAsString(), cls);
                }
            }
            // не затираем существующие файлы и сам внешний класс
            Set<String> classes = new LinkedHashSet<>(dest.values());
            for (String c : classes) {
                if (taken.contains(dir + c + ".java") || c.equals(outer.getNameAsString())) {
                    notes.add("группа " + c + " пропущена: файл " + dir + c + ".java уже существует");
                    dest.values().removeIf(c::equals);
                }
            }
            if (dest.isEmpty()) continue;

            // Moving a method without its shared fields changes both binding and initialization order.
            // Keep such methods in the owner; rewriteCalls will qualify calls from extracted helpers.
            Set<String> fields = new HashSet<>();
            outer.getFields().forEach(field -> field.getVariables().forEach(v -> fields.add(v.getNameAsString())));
            for (MethodDeclaration method : statics) {
                if (method.findAll(NameExpr.class).stream().anyMatch(n -> fields.contains(n.getNameAsString()))) {
                    dest.remove(method.getNameAsString());
                    notes.add("метод " + method.getNameAsString() + " оставлен в " + outer.getNameAsString() + ": использует поле");
                }
            }
            if (dest.isEmpty()) continue;

            Set<String> staying = new HashSet<>();
            for (MethodDeclaration md : outer.getMethods()) if (md.isStatic() && !dest.containsKey(md.getNameAsString())) staying.add(md.getNameAsString());
            Set<String> mustBeVisible = new HashSet<>();
            rewriteCalls(outer, dest, staying, mustBeVisible);

            Map<String, CompilationUnit> units = new LinkedHashMap<>();
            for (MethodDeclaration md : new ArrayList<>(statics)) {
                String cls = dest.get(md.getNameAsString());
                if (cls == null) continue;
                CompilationUnit ncu = units.computeIfAbsent(cls, c -> newUnit(cu, c));
                MethodDeclaration copy = md.clone();
                copy.setModifier(Modifier.Keyword.PRIVATE, false);
                ncu.getType(0).addMember(copy);
                md.remove();
            }
            for (MethodDeclaration md : outer.getMethods()) {
                if (md.isStatic() && mustBeVisible.contains(md.getNameAsString())) md.setModifier(Modifier.Keyword.PRIVATE, false);
            }
            for (var e : units.entrySet()) {
                String np = dir + e.getKey() + ".java";
                taken.add(np);
                created.add(new ProjectFile(np, e.getValue().toString()));
                List<String> names = new ArrayList<>();
                dest.forEach((n, c) -> { if (c.equals(e.getKey())) names.add(n); });
                notes.add("создан " + e.getKey() + " (" + String.join(", ", names) + ")");
            }
            pm.touch(path);
        }
    }

    private static CompilationUnit newUnit(CompilationUnit src, String className) {
        CompilationUnit ncu = new CompilationUnit();
        src.getPackageDeclaration().ifPresent(p -> ncu.setPackageDeclaration(p.clone()));
        src.getImports().forEach(i -> ncu.addImport(i.clone()));
        ClassOrInterfaceDeclaration c = ncu.addClass(className);
        c.setModifier(Modifier.Keyword.PUBLIC, false);
        c.setModifier(Modifier.Keyword.FINAL, true);
        c.addConstructor(Modifier.Keyword.PRIVATE);
        return ncu;
    }

    /** Вызовы без квалификатора → Класс.метод, если вызывающий и вызываемый теперь в разных классах. */
    private static void rewriteCalls(ClassOrInterfaceDeclaration outer, Map<String, String> dest, Set<String> staying,
                                     Set<String> mustBeVisible) {
        String outerName = outer.getNameAsString();
        for (MethodCallExpr mc : outer.findAll(MethodCallExpr.class)) {
            if (mc.getScope().isPresent()) continue;
            String name = mc.getNameAsString();
            String target = dest.containsKey(name) ? dest.get(name) : (staying.contains(name) ? outerName : null);
            if (target == null) continue;
            TypeDeclaration<?> enclType = mc.findAncestor(TypeDeclaration.class).orElse(null);
            String origin;
            if (enclType != outer) {
                if (enclType == null || !enclType.getMethodsByName(name).isEmpty()) continue; // вызов собственного метода вложенного типа
                if (target.equals(outerName)) continue;                                      // вложенный тип и так видит статику внешнего
                origin = enclType.getNameAsString();
            } else {
                CallableDeclaration<?> encl = mc.findAncestor(CallableDeclaration.class).orElse(null);
                origin = encl instanceof MethodDeclaration m && dest.containsKey(m.getNameAsString())
                        ? dest.get(m.getNameAsString()) : outerName;
            }
            if (origin.equals(target)) continue;
            mc.setScope(new NameExpr(target));
            if (target.equals(outerName)) mustBeVisible.add(name);
        }
    }

    /** Parser / Analyzer / Optimizer по смыслу метода; null — остаётся в исходном классе (вывод, main-логика). */
    static String classify(MethodDeclaration md) {
        String body = md.getBody().map(Object::toString).orElse("");
        if (OPTIMIZED.matcher(body).find()) return "Optimizer";
        if (PARSER.matcher(md.getNameAsString()).find()) return "Parser";
        if (ANALYZER.matcher(md.getNameAsString()).find()) return "Analyzer";
        return null;
    }

    private String compileNote(List<ProjectFile> files) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("bp-dec-");
            CompilerService.CompileResult r = compiler.compile(files, dir);
            if (!r.ok()) throw new CompileErrorException("Декомпозиция отклонена: результат не компилируется — " + r.output());
            return "Компиляция после декомпозиции: успешно.";
        } catch (IOException e) {
            throw new IllegalStateException("Компиляцию результата декомпозиции проверить не удалось", e);
        } finally {
            RunServiceImpl.deleteQuietly(dir);
        }
    }
}

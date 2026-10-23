package com.axenix.blueprints.parser;

import com.axenix.blueprints.model.Dto.ProjectFile;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Разобранный проект: AST каждого файла (с включённым lexical preserving) и индекс методов.
 * Любая правка AST должна идти через этот объект, чтобы печать сохраняла форматирование.
 */
public final class ProjectModel {

    public record MethodRef(String id, String className, String file, CompilationUnit cu, CallableDeclaration<?> decl) {
        /** Тело метода или null (abstract / interface). */
        public BlockStmt body() {
            if (decl instanceof MethodDeclaration md) return md.getBody().orElse(null);
            if (decl instanceof ConstructorDeclaration cd) return cd.getBody();
            return null;
        }
    }

    private static ParserConfiguration config() {
        // BLEEDING_EDGE: разбираем всё, что умеет парсер (record, switch-выражения, текстовые блоки и т.д.)
        return new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
    }

    public final Map<String, CompilationUnit> units = new LinkedHashMap<>();
    public final Map<String, String> sources = new LinkedHashMap<>();
    public final List<MethodRef> methods = new ArrayList<>();
    private final Set<String> touched = new LinkedHashSet<>();

    private ProjectModel() {}

    public static ProjectModel parse(List<ProjectFile> files) {
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("Список файлов пуст");
        ProjectModel pm = new ProjectModel();
        JavaParser parser = new JavaParser(config());
        Map<String, Integer> seen = new HashMap<>();
        for (ProjectFile f : files) {
            if (f.path() == null || f.source() == null) throw new IllegalArgumentException("У файла нет path или source");
            CompilationUnit cu = parseUnit(parser, f.path(), f.source());
            pm.units.put(f.path(), cu);
            pm.sources.put(f.path(), f.source());
            for (CallableDeclaration<?> d : cu.findAll(CallableDeclaration.class)) {
                String cls = className(d);
                String base = cls + "." + d.getNameAsString();
                int n = seen.merge(base, 1, Integer::sum);
                pm.methods.add(new MethodRef(n == 1 ? base : base + "#" + n, cls, f.path(), cu, d));
            }
        }
        return pm;
    }

    /** Разбор одного фрагмента; при ошибке бросает JavaParseException с номером строки. */
    public static CompilationUnit parseUnit(JavaParser parser, String path, String source) {
        ParseResult<CompilationUnit> r = parser.parse(source);
        if (!r.isSuccessful() || r.getResult().isEmpty()) {
            Problem p = r.getProblems().isEmpty() ? null : r.getProblems().get(0);
            Integer line = p == null ? null
                    : p.getLocation().flatMap(l -> l.getBegin().getRange()).map(rg -> rg.begin.line).orElse(null);
            String msg = p == null ? "неизвестная ошибка" : p.getMessage();
            throw new JavaParseException(path + (line == null ? "" : ":" + line) + " — " + msg, line);
        }
        CompilationUnit cu = r.getResult().get();
        LexicalPreservingPrinter.setup(cu);
        return cu;
    }

    public static JavaParser newParser() {
        return new JavaParser(config());
    }

    /** Пакет + цепочка внешних типов: com.example.Main.Stats */
    static String className(CallableDeclaration<?> d) {
        Deque<String> parts = new ArrayDeque<>();
        Node n = d.getParentNode().orElse(null);
        while (n != null) {
            if (n instanceof TypeDeclaration<?> t) parts.addFirst(t.getNameAsString());
            n = n.getParentNode().orElse(null);
        }
        d.findCompilationUnit().flatMap(cu -> cu.getPackageDeclaration()).ifPresent(p -> parts.addFirst(p.getNameAsString()));
        return String.join(".", parts);
    }

    public MethodRef method(String id) {
        for (MethodRef m : methods) if (m.id().equals(id)) return m;
        throw new IllegalArgumentException("Метод не найден: " + id);
    }

    public void touch(String path) {
        touched.add(path);
    }

    /** Файлы в исходном порядке; нетронутые возвращаются байт-в-байт. */
    public List<ProjectFile> render() {
        List<ProjectFile> out = new ArrayList<>();
        for (Map.Entry<String, CompilationUnit> e : units.entrySet()) {
            String path = e.getKey();
            if (!touched.contains(path)) {
                out.add(new ProjectFile(path, sources.get(path)));
                continue;
            }
            String text;
            try {
                text = LexicalPreservingPrinter.print(e.getValue());
            } catch (RuntimeException ex) {
                text = e.getValue().toString(); // запасной путь: обычный pretty printer
            }
            out.add(new ProjectFile(path, text));
        }
        return out;
    }

    public int totalLines() {
        int n = 0;
        for (String s : sources.values()) n += (int) s.lines().count();
        return n;
    }
}

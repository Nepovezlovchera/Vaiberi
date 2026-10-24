package com.axenix.blueprints.parser;

import com.axenix.blueprints.model.Dto.ProjectFile;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
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

    /**
     * Индексированный исполняемый член: метод, конструктор или компактный конструктор record.
     * decl — CallableDeclaration либо CompactConstructorDeclaration (она в JavaParser не CallableDeclaration).
     */
    public record MethodRef(String id, String className, String file, CompilationUnit cu, Node decl) {
        public MethodRef {
            if (!(decl instanceof CallableDeclaration<?> || decl instanceof CompactConstructorDeclaration)) {
                throw new IllegalArgumentException("Не метод: " + decl.getClass().getSimpleName());
            }
        }

        /** Тело метода или null (abstract / interface). */
        public BlockStmt body() {
            if (decl instanceof MethodDeclaration md) return md.getBody().orElse(null);
            if (decl instanceof ConstructorDeclaration cd) return cd.getBody();
            if (decl instanceof CompactConstructorDeclaration cc) return cc.getBody();
            return null;
        }

        /** Сигнатура для подписи узла входа; у компактного конструктора — «Point (compact)». */
        public String signature() {
            if (decl instanceof CallableDeclaration<?> cd) return cd.getDeclarationAsString(true, true, true);
            CompactConstructorDeclaration cc = (CompactConstructorDeclaration) decl;
            StringBuilder sb = new StringBuilder();
            cc.getModifiers().forEach(mod -> sb.append(mod.getKeyword().asString()).append(' '));
            return sb.append(cc.getNameAsString()).append(" (compact)").toString();
        }

        /** Параметры; у компактного конструктора — компоненты record (они неявные параметры). */
        public List<Parameter> parameters() {
            if (decl instanceof CallableDeclaration<?> cd) return cd.getParameters();
            return decl.findAncestor(RecordDeclaration.class).map(r -> (List<Parameter>) r.getParameters()).orElse(List.of());
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
            Map<ObjectCreationExpr, Integer> anon = anonymousIndexes(cu);
            // обход в прямом порядке = порядок исходника; компактные конструкторы — в общем потоке
            for (Node d : cu.findAll(Node.class, x -> x instanceof CallableDeclaration<?> || x instanceof CompactConstructorDeclaration)) {
                String cls = className(d, anon);
                String base = cls + "." + ((NodeWithSimpleName<?>) d).getNameAsString();
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

    /**
     * Пакет + цепочка внешних типов: com.example.Main.Stats. Анонимный класс даёт сегмент в стиле javac:
     * Main$1, вложенный в него — Main$1$1 (номер — порядок в исходнике среди анонимных классов того же
     * объемлющего класса). Локальный класс в методе именуется своим именем: Main.Local.
     */
    static String className(Node d, Map<ObjectCreationExpr, Integer> anon) {
        Deque<String> parts = new ArrayDeque<>(); // ".Name" или "$k", от внутреннего к внешнему
        Node child = d;
        Node n = d.getParentNode().orElse(null);
        while (n != null) {
            if (n instanceof TypeDeclaration<?> t) parts.addFirst("." + t.getNameAsString());
            else if (n instanceof ObjectCreationExpr oce && isAnonymousMember(oce, child)) parts.addFirst("$" + anon.get(oce));
            child = n;
            n = n.getParentNode().orElse(null);
        }
        StringBuilder sb = new StringBuilder();
        d.findCompilationUnit().flatMap(cu -> cu.getPackageDeclaration()).ifPresent(p -> sb.append(p.getNameAsString()));
        for (String p : parts) sb.append(p);
        return sb.charAt(0) == '.' ? sb.substring(1) : sb.toString();
    }

    /** child — член тела анонимного класса oce (а не аргумент/скоуп выражения new). */
    private static boolean isAnonymousMember(ObjectCreationExpr oce, Node child) {
        return child instanceof BodyDeclaration<?> && oce.getAnonymousClassBody().map(b -> b.stream().anyMatch(x -> x == child)).orElse(false);
    }

    /** Номер каждого анонимного класса файла внутри его ближайшего объемлющего класса (именованного или анонимного). */
    private static Map<ObjectCreationExpr, Integer> anonymousIndexes(CompilationUnit cu) {
        Map<ObjectCreationExpr, Integer> out = new IdentityHashMap<>();
        Map<Node, Integer> counters = new IdentityHashMap<>();
        for (ObjectCreationExpr oce : cu.findAll(ObjectCreationExpr.class, o -> o.getAnonymousClassBody().isPresent())) {
            Node owner = null;
            Node child = oce;
            Node n = oce.getParentNode().orElse(null);
            while (n != null && owner == null) {
                if (n instanceof TypeDeclaration<?>) owner = n;
                else if (n instanceof ObjectCreationExpr o && isAnonymousMember(o, child)) owner = n;
                child = n;
                n = n.getParentNode().orElse(null);
            }
            out.put(oce, counters.merge(owner == null ? cu : owner, 1, Integer::sum));
        }
        return out;
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
                // LexicalPreservingPrinter иногда портит редкие конструкции (напр. switch-выражения со стрелкой):
                // результат обязан заново разбираться, иначе мягкий откат к обычному pretty printer
                if (!newParser().parse(text).isSuccessful()) text = e.getValue().toString();
            } catch (RuntimeException ex) {
                text = e.getValue().toString();
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

package com.axenix.blueprints.optimizer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Детерминированные автофиксы. Любой фикс — только при полностью распознанном шаблоне;
 * иначе возвращается пустой Optional и клиент решает, звать ли LLM.
 */
public final class TemplateFixes {
    private TemplateFixes() {}

    private static final Set<String> MUTATORS = Set.of("add", "addAll", "remove", "removeAll", "removeIf", "retainAll",
            "clear", "set", "addFirst", "addLast", "removeFirst", "removeLast", "sort", "replaceAll");

    /**
     * list.contains(x) внутри цикла → HashSet.
     * Шаблон A: {@code if (!list.contains(x)) {... list.add(x) ...}} при единственной мутации add →
     *           {@code if (seen.add(x))}.
     * Шаблон B: список в цикле не меняется → {@code seen.contains(x)} по копии в HashSet.
     *
     * @return описание сделанной замены или пусто, если шаблон не подошёл (AST не тронут)
     */
    public static Optional<String> containsInLoop(CompilationUnit cu, int line) {
        MethodCallExpr call = null;
        for (MethodCallExpr mc : cu.findAll(MethodCallExpr.class)) {
            if (mc.getNameAsString().equals("contains") && mc.getArguments().size() == 1
                    && mc.getScope().filter(Expression::isNameExpr).isPresent()
                    && mc.getRange().map(r -> r.begin.line).orElse(-1) == line) {
                call = mc;
                break;
            }
        }
        if (call == null) return Optional.empty();
        String var = call.getScope().get().asNameExpr().getNameAsString();

        Statement loop = enclosingLoop(call);
        if (loop == null || !(loop.getParentNode().orElse(null) instanceof BlockStmt block)) return Optional.empty();
        CallableDeclaration<?> method = call.findAncestor(CallableDeclaration.class).orElse(null);
        if (method == null) return Optional.empty();

        Type declared = declaredType(method, var);
        if (!(declared instanceof ClassOrInterfaceType ct) || !isList(ct)) return Optional.empty();
        if (ct.getTypeArguments().isEmpty() || ct.getTypeArguments().get().size() != 1) return Optional.empty();
        String elementType = ct.getTypeArguments().get().get(0).toString();

        // переменная должна быть объявлена вне цикла
        for (VariableDeclarator vd : loop.findAll(VariableDeclarator.class)) {
            if (vd.getNameAsString().equals(var)) return Optional.empty();
        }

        // какие мутации списка есть в цикле
        Set<String> mutations = new HashSet<>();
        for (MethodCallExpr mc : loop.findAll(MethodCallExpr.class)) {
            if (mc.getScope().filter(s -> s.isNameExpr() && s.asNameExpr().getNameAsString().equals(var)).isPresent()
                    && MUTATORS.contains(mc.getNameAsString())) mutations.add(mc.getNameAsString());
        }
        // список не должен "утекать" в вызовы других методов, которые могли бы его менять
        for (MethodCallExpr mc : loop.findAll(MethodCallExpr.class)) {
            for (Expression a : mc.getArguments()) {
                if (a.isNameExpr() && a.asNameExpr().getNameAsString().equals(var)) return Optional.empty();
            }
        }

        String setName = freshName(method, var + "Set");
        Expression arg = call.getArgument(0);
        JavaParser parser = new JavaParser();
        String declText = "Set<" + elementType + "> " + setName + " = new HashSet<>(" + var + ");";
        Statement decl = parseStmt(parser, declText);
        if (decl == null) return Optional.empty();

        String explanation;
        if (mutations.isEmpty()) {
            Expression replacement = parseExpr(parser, setName + ".contains(" + arg + ")");
            if (replacement == null) return Optional.empty();
            call.replace(replacement);
            explanation = "list.contains в цикле заменён на HashSet.contains: O(n²) → O(n).";
        } else if (mutations.equals(Set.of("add")) && call.getParentNode().orElse(null) instanceof UnaryExpr ue
                && ue.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT
                && ue.getParentNode().orElse(null) instanceof IfStmt is && is.getCondition() == ue) {
            Expression replacement = parseExpr(parser, setName + ".add(" + arg + ")");
            if (replacement == null) return Optional.empty();
            ue.replace(replacement);
            explanation = "if (!" + var + ".contains(x)) заменён на if (" + setName + ".add(x)): O(n²) → O(n).";
        } else {
            return Optional.empty();
        }
        block.getStatements().add(block.getStatements().indexOf(loop), decl);
        ensureImport(cu, "java.util.Set");
        ensureImport(cu, "java.util.HashSet");
        return Optional.of(explanation + " Перед циклом добавлено: " + declText);
    }


    /** Любой подходящий шаблон для проблемы, начинающейся на строке line (для отчёта и автофикса). */
    public static Optional<String> apply(CompilationUnit cu, int line) {
        Optional<String> r = containsInLoop(cu, line);
        return r.isPresent() ? r : nestedLoopToMap(cu, line);
    }

    /**
     * Вложенные foreach с сопоставлением по ключу:
     * <pre>
     * for (A a : xs) { for (B b : ys) { if (a.key.equals(b.key)) BODY } }
     * </pre>
     * заменяются индексом по ключу (HashMap&lt;Object, List&lt;B&gt;&gt;), построенным один раз перед внешним циклом.
     * Порядок обхода совпадающих элементов и число срабатываний BODY сохраняются (в индексе — список, а не один элемент).
     * Шаблон применяется только если внутренняя коллекция не меняется во внешнем цикле и ключи зависят
     * ровно от одной из переменных циклов; иначе AST не трогается.
     */
    public static Optional<String> nestedLoopToMap(CompilationUnit cu, int line) {
        ForEachStmt outer = null;
        for (ForEachStmt f : cu.findAll(ForEachStmt.class)) {
            if (f.getRange().map(r -> r.begin.line).orElse(-1) == line) {
                outer = f;
                break;
            }
        }
        if (outer == null || !(outer.getParentNode().orElse(null) instanceof BlockStmt outerBlock)) return Optional.empty();
        if (!(outer.getBody() instanceof BlockStmt body)) return Optional.empty();
        ForEachStmt inner = null;
        for (Statement st : body.getStatements()) {
            if (st instanceof ForEachStmt fe) {
                inner = fe;
                break;
            }
        }
        CallableDeclaration<?> method = outer.findAncestor(CallableDeclaration.class).orElse(null);
        if (inner == null || method == null) return Optional.empty();

        String a = outer.getVariable().getVariable(0).getNameAsString();
        String b = inner.getVariable().getVariable(0).getNameAsString();
        Type bType = inner.getVariable().getCommonType();
        if (bType.isVarType() || bType.isPrimitiveType()) return Optional.empty();
        Expression source = inner.getIterable();
        if (!source.isNameExpr() || refs(source, a)) return Optional.empty();

        // тело внутреннего цикла: единственный if без else с условием x.equals(y)
        Statement ib = inner.getBody();
        Statement only = ib instanceof BlockStmt blk ? (blk.getStatements().size() == 1 ? blk.getStatement(0) : null) : ib;
        if (!(only instanceof IfStmt iff) || iff.getElseStmt().isPresent()) return Optional.empty();
        if (!(iff.getCondition() instanceof MethodCallExpr eq) || !eq.getNameAsString().equals("equals")
                || eq.getScope().isEmpty() || eq.getArguments().size() != 1) return Optional.empty();
        Expression s1 = eq.getScope().get();
        Expression s2 = eq.getArgument(0);
        Expression keyA;
        Expression keyB;
        if (refs(s1, a) && !refs(s1, b) && refs(s2, b) && !refs(s2, a)) {
            keyA = s1;
            keyB = s2;
        } else if (refs(s2, a) && !refs(s2, b) && refs(s1, b) && !refs(s1, a)) {
            keyA = s2;
            keyB = s1;
        } else {
            return Optional.empty();
        }
        // ключ внутреннего элемента вычисляется вне внешнего цикла: только по b, без чужих имён и безымянных вызовов
        for (NameExpr n : keyB.findAll(NameExpr.class)) if (!n.getNameAsString().equals(b)) return Optional.empty();
        for (MethodCallExpr mc : keyB.findAll(MethodCallExpr.class)) if (mc.getScope().isEmpty()) return Optional.empty();

        // внутренняя коллекция не меняется и не утекает во внешнем цикле
        String src = source.asNameExpr().getNameAsString();
        for (MethodCallExpr mc : outer.findAll(MethodCallExpr.class)) {
            if (mc.getScope().filter(x -> x.isNameExpr() && x.asNameExpr().getNameAsString().equals(src)).isPresent()
                    && MUTATORS.contains(mc.getNameAsString())) return Optional.empty();
            for (Expression arg : mc.getArguments()) {
                if (arg.isNameExpr() && arg.asNameExpr().getNameAsString().equals(src)) return Optional.empty();
            }
        }
        for (AssignExpr as : outer.findAll(AssignExpr.class)) {
            if (as.getTarget().isNameExpr() && as.getTarget().asNameExpr().getNameAsString().equals(src)) return Optional.empty();
            String t = as.getTarget().toString();
            if (t.equals(keyA.toString()) || t.equals(keyB.toString())) return Optional.empty(); // меняется сам ключ
        }

        String elem = bType.toString();
        String mapName = freshName(method, b + "ByKey");
        String keyParam = freshName(method, "key");
        String matches = freshName(method, "matches");
        JavaParser parser = new JavaParser();
        Statement mapDecl = parseStmt(parser, "Map<Object, List<" + elem + ">> " + mapName + " = new HashMap<>();");
        Statement fill = parseStmt(parser, "for (" + elem + " " + b + " : " + src + ") { " + mapName + ".computeIfAbsent("
                + keyB + ", " + keyParam + " -> new ArrayList<>()).add(" + b + "); }");
        Statement lookup = parseStmt(parser, "List<" + elem + "> " + matches + " = " + mapName + ".get(" + keyA + ");");
        Statement guard = parseStmt(parser, "if (" + matches + " != null) { for (" + elem + " " + b + " : " + matches + ") { } }");
        if (mapDecl == null || fill == null || lookup == null || guard == null) return Optional.empty();
        ForEachStmt newInner = guard.asIfStmt().getThenStmt().asBlockStmt().getStatement(0).asForEachStmt();
        newInner.setBody(iff.getThenStmt().clone());

        int idx = body.getStatements().indexOf(inner);
        body.getStatements().add(idx, lookup);
        body.getStatements().add(idx + 1, guard);
        body.getStatements().remove(inner);
        int at = outerBlock.getStatements().indexOf(outer);
        outerBlock.getStatements().add(at, mapDecl);
        outerBlock.getStatements().add(at + 1, fill);
        ensureImport(cu, "java.util.Map");
        ensureImport(cu, "java.util.HashMap");
        ensureImport(cu, "java.util.List");
        ensureImport(cu, "java.util.ArrayList");
        return Optional.of("Вложенный поиск по ключу заменён индексом HashMap, построенным один раз перед внешним циклом: O(n²) → O(n). "
                + "Добавлено: Map<Object, List<" + elem + ">> " + mapName + ".");
    }

    private static boolean refs(Expression e, String name) {
        for (NameExpr n : e.findAll(NameExpr.class)) if (n.getNameAsString().equals(name)) return true;
        return false;
    }

    // ---------- helpers ----------

    private static boolean isList(ClassOrInterfaceType t) {
        String n = t.getNameAsString();
        return n.equals("List") || n.equals("ArrayList") || n.equals("LinkedList");
    }

    private static Statement enclosingLoop(Node from) {
        Node n = from.getParentNode().orElse(null);
        while (n != null && !(n instanceof CallableDeclaration)) {
            if (n instanceof ForEachStmt || n instanceof ForStmt || n instanceof WhileStmt || n instanceof DoStmt) {
                return (Statement) n;
            }
            n = n.getParentNode().orElse(null);
        }
        return null;
    }

    private static Type declaredType(CallableDeclaration<?> method, String var) {
        for (Parameter p : method.getParameters()) if (p.getNameAsString().equals(var)) return p.getType();
        for (VariableDeclarator v : method.findAll(VariableDeclarator.class)) if (v.getNameAsString().equals(var)) return v.getType();
        return null;
    }

    private static String freshName(CallableDeclaration<?> method, String base) {
        Set<String> used = new HashSet<>();
        for (SimpleName sn : method.findAll(SimpleName.class)) used.add(sn.getIdentifier());
        String name = base;
        int k = 2;
        while (used.contains(name)) name = base + k++;
        return name;
    }

    private static Statement parseStmt(JavaParser p, String code) {
        ParseResult<Statement> r = p.parseStatement(code);
        return r.isSuccessful() ? r.getResult().orElse(null) : null;
    }

    private static Expression parseExpr(JavaParser p, String code) {
        ParseResult<Expression> r = p.parseExpression(code);
        return r.isSuccessful() ? r.getResult().orElse(null) : null;
    }

    static void ensureImport(CompilationUnit cu, String fqn) {
        String pkg = fqn.substring(0, fqn.lastIndexOf('.'));
        boolean present = cu.getImports().stream().anyMatch(i ->
                !i.isStatic() && (i.getNameAsString().equals(fqn) || (i.isAsterisk() && i.getNameAsString().equals(pkg))));
        if (!present) cu.addImport(fqn);
    }
}

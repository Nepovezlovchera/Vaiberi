package com.axenix.blueprints.optimizer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
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

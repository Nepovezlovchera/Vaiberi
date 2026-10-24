package com.axenix.blueprints.optimizer;

import com.axenix.blueprints.analyzer.AnalyzerServiceImpl;
import com.axenix.blueprints.parser.ProjectModel;
import com.axenix.blueprints.parser.ProjectModel.MethodRef;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SuperExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.Type;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Автофикс DUPLICATION.
 * <ul>
 *   <li>{@link #condition}: одно и то же условие if в нескольких местах заменяется вызовом общего
 *       {@code private static boolean}-метода (условие переезжает в него как есть, порядок вычислений не меняется);</li>
 *   <li>{@link #method}: метод-дубликат делегирует вызов первому методу с таким же телом.</li>
 * </ul>
 * При любом сомнении (поля, this, присваивания в условии, var, generic-классы) AST не трогается.
 */
public final class DuplicationFix {

    private DuplicationFix() {}

    // ------------------------------------------------------------ повторяющееся условие

    public static Optional<String> condition(CompilationUnit cu, int line) {
        IfStmt target = null;
        for (IfStmt s : cu.findAll(IfStmt.class)) {
            if (s.getCondition().getRange().map(r -> r.begin.line).orElse(-1) == line) {
                target = s;
                break;
            }
        }
        if (target == null) return Optional.empty();
        String key = norm(target.getCondition());
        List<IfStmt> group = new ArrayList<>();
        for (IfStmt s : cu.findAll(IfStmt.class)) if (norm(s.getCondition()).equals(key)) group.add(s);
        if (group.size() < 2) return Optional.empty();

        IfStmt first = target;
        Expression sample = first.getCondition();
        if (!simple(sample)) return Optional.empty();
        TypeDeclaration<?> owner = first.findAncestor(TypeDeclaration.class).orElse(null);
        if (owner == null || owner.isClassOrInterfaceDeclaration()
                && !owner.asClassOrInterfaceDeclaration().getTypeParameters().isEmpty()) return Optional.empty();
        Map<String, String> sig = signature(first, sample);
        if (sig == null || sig.isEmpty()) return Optional.empty();

        List<IfStmt> same = new ArrayList<>();
        for (IfStmt s : group) {
            if (s.findAncestor(TypeDeclaration.class).orElse(null) != owner) continue;
            if (sig.equals(signature(s, s.getCondition()))) same.add(s);
        }
        if (same.size() < 2) return Optional.empty();

        Set<String> existing = owner.getMethods().stream().map(MethodDeclaration::getNameAsString).collect(Collectors.toSet());
        String base = key.matches("(\\w+)==null\\|\\|\\1\\.isEmpty\\(\\)") ? "isNullOrEmpty" : "condition";
        String name = base;
        for (int k = 2; existing.contains(name); k++) name = base + k;

        String params = sig.entrySet().stream().map(e -> e.getValue() + " " + e.getKey()).collect(Collectors.joining(", "));
        String args = String.join(", ", sig.keySet());
        JavaParser p = new JavaParser();
        var parsed = p.parseBodyDeclaration("private static boolean " + name + "(" + params + ") { return "
                + sample.toString() + "; }");
        if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) return Optional.empty();
        BodyDeclaration<?> helper = parsed.getResult().get();
        var call = p.parseExpression(name + "(" + args + ")");
        if (!call.isSuccessful()) return Optional.empty();

        int firstLine = first.getBegin().map(b -> b.line).orElse(0);
        for (IfStmt s : same) s.getCondition().replace(call.getResult().get().clone());
        Node anchor = first.findAncestor(MethodDeclaration.class).map(m -> (Node) m).orElse(null);
        int idx = anchor == null ? owner.getMembers().size() - 1 : owner.getMembers().indexOf(anchor);
        owner.getMembers().add(Math.max(idx, -1) + 1, helper);
        return Optional.of("Условие «" + key + "» вынесено в метод «" + name + "(" + String.join(", ", sig.keySet())
                + ")»; заменено вхождений: " + same.size() + " (в т.ч. строка " + firstLine + ").");
    }

    private static String norm(Expression e) {
        return e.toString().replaceAll("\\s+", "");
    }

    /** Условие без побочных эффектов на окружение, которое можно перенести в статический метод. */
    private static boolean simple(Expression e) {
        if (!e.findAll(AssignExpr.class).isEmpty() || !e.findAll(ThisExpr.class).isEmpty()
                || !e.findAll(SuperExpr.class).isEmpty() || !e.findAll(LambdaExpr.class).isEmpty()
                || !e.findAll(ObjectCreationExpr.class).isEmpty()) return false;
        for (UnaryExpr u : e.findAll(UnaryExpr.class)) {
            switch (u.getOperator()) {
                case PREFIX_INCREMENT, PREFIX_DECREMENT, POSTFIX_INCREMENT, POSTFIX_DECREMENT -> { return false; }
                default -> { }
            }
        }
        for (MethodCallExpr c : e.findAll(MethodCallExpr.class)) if (c.getScope().isEmpty()) return false;
        return true;
    }

    /** имя → тип для переменных условия, объявленных в методе; null, если что-то нельзя разрешить. */
    private static Map<String, String> signature(IfStmt at, Expression cond) {
        CallableDeclaration<?> m = at.findAncestor(CallableDeclaration.class).orElse(null);
        if (m == null || !m.getTypeParameters().isEmpty()) return null;
        Map<String, String> res = new LinkedHashMap<>();
        for (NameExpr n : cond.findAll(NameExpr.class)) {
            String id = n.getNameAsString();
            if (res.containsKey(id)) continue;
            Type t = null;
            for (Parameter p : m.getParameters()) if (p.getNameAsString().equals(id)) t = p.getType();
            if (t == null) for (VariableDeclarator v : m.findAll(VariableDeclarator.class)) {
                if (v.getNameAsString().equals(id)) t = v.getType();
            }
            if (t == null) {
                // имя типа/enum в начале выражения: Type.method(...) или Type.CONSTANT (например, OrderKind.LARGE)
                boolean typeName = Character.isUpperCase(id.charAt(0)) && n.getParentNode()
                        .filter(pn -> pn instanceof MethodCallExpr mc && mc.getScope().filter(sc -> sc == n).isPresent()
                                || pn instanceof com.github.javaparser.ast.expr.FieldAccessExpr fa
                                && fa.getScope() == n).isPresent();
                if (typeName) continue;
                return null;
            }
            if (t.isVarType()) return null;
            res.put(id, t.toString());
        }
        return res;
    }

    // ------------------------------------------------------------ метод-дубликат

    public static Optional<String> method(ProjectModel pm, String methodId) {
        MethodRef dup = pm.methods.stream().filter(m -> m.id().equals(methodId)).findFirst().orElse(null);
        if (dup == null || !(dup.decl() instanceof MethodDeclaration dm) || dm.getBody().isEmpty()) return Optional.empty();
        String key = AnalyzerServiceImpl.normalizedBody(dup);
        MethodRef first = null;
        for (MethodRef m : pm.methods) {
            if (m == dup || m.body() == null || !(m.decl() instanceof MethodDeclaration)) continue;
            if (AnalyzerServiceImpl.normalizedBody(m).equals(key)) {
                first = m;
                break;
            }
        }
        if (first == null) return Optional.empty();
        MethodDeclaration fm = (MethodDeclaration) first.decl();
        if (dm.getParentNode().orElse(null) != fm.getParentNode().orElse(null)) return Optional.empty();
        if (!dm.getTypeParameters().isEmpty() || (dm.isStatic() && !fm.isStatic())) return Optional.empty();
        String args = dm.getParameters().stream().map(Parameter::getNameAsString).collect(Collectors.joining(", "));
        String call = fm.getNameAsString() + "(" + args + ")";
        Statement st = new JavaParser().parseStatement(dm.getType().isVoidType() ? call + ";" : "return " + call + ";")
                .getResult().orElse(null);
        if (st == null) return Optional.empty();
        NodeList<Statement> list = new NodeList<>();
        list.add(st);
        dm.setBody(new BlockStmt(list));
        return Optional.of("Метод «" + dm.getNameAsString() + "» делегирует вызов «" + fm.getNameAsString()
                + "» (тела совпадали с точностью до имён переменных).");
    }
}

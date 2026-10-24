package com.axenix.blueprints.optimizer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.LocalClassDeclarationStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.stmt.YieldStmt;
import com.github.javaparser.ast.type.Type;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Детерминированный автофикс LONG_METHOD: «Извлечь метод» (Extract Method).
 *
 * <p>Из тела метода выбирается непрерывная серия операторов верхнего уровня, которую можно вынести в
 * {@code private}-метод без изменения поведения. Выбор консервативный — при любом сомнении AST не трогается:
 * <ul>
 *   <li>в серии нет {@code return}/{@code yield}, {@code break}/{@code continue} выходят только за пределы серии
 *       внутри неё самой, нет меток;</li>
 *   <li>переменные, объявленные до серии и используемые в ней, становятся параметрами
 *       (переменная без инициализатора — отказ);</li>
 *   <li>наружу можно вернуть не более одного значения: либо переприсвоенную внешнюю переменную,
 *       либо переменную, объявленную в серии и нужную после неё; иначе отказ;</li>
 *   <li>тип каждой передаваемой/возвращаемой переменной должен быть явным (не {@code var});</li>
 *   <li>{@code throws} и type-параметры исходного метода копируются в новый метод.</li>
 * </ul>
 * Из подходящих серий берётся та, что делит метод на наиболее равные части (чтобы оба метода стали короче).
 * Сохранение поведения всё равно подтверждается Quality Gate.
 */
public final class ExtractMethod {

    // Небольшие блоки нужны для повторного запуска автофикса на уже созданном
    // Part-методе: после первого извлечения в нём часто остаётся 3–4 строки
    // безопасной логики, которую также можно вынести.
    private static final int MIN_SEGMENT_LINES = 3;

    private ExtractMethod() {}

    /** @param line строка начала проблемы (начало метода) */
    public static Optional<String> apply(CompilationUnit cu, int line) {
        MethodDeclaration m = findMethod(cu, line);
        if (m == null || m.getBody().isEmpty() || !(m.getParentNode().orElse(null) instanceof TypeDeclaration<?>)) {
            return Optional.empty();
        }
        BlockStmt body = m.getBody().get();
        List<Statement> st = new ArrayList<>(body.getStatements());
        int methodSpan = span(m);
        Plan best = null;
        long bestScore = -1;
        for (int i = 0; i < st.size(); i++) {
            for (int j = i; j < st.size(); j++) {
                int seg = span(st.get(i), st.get(j));
                if (seg < MIN_SEGMENT_LINES || seg >= methodSpan - 2) continue;
                long score = Math.min(seg, methodSpan - seg);
                if (score <= bestScore) continue;
                Plan p = plan(m, st, i, j);
                if (p != null) {
                    best = p;
                    bestScore = score;
                }
            }
        }
        // Даже если все непрерывные серии короче обычного порога, безопасный
        // одиночный оператор всё равно уменьшает метод и позволяет завершить
        // цепочку автофиксов. plan() сохраняет проверки областей видимости и
        // возвращаемого значения.
        if (best == null) {
            for (int i = 0; i < st.size(); i++) {
                Plan p = plan(m, st, i, i);
                if (p != null && span(st.get(i)) > 0) {
                    best = p;
                    break;
                }
            }
        }
        if (best == null) return Optional.empty();
        return Optional.of(build(m, body, st, best));
    }

    // ---------------------------------------------------------------- planning

    private record Plan(int from, int to, Map<String, Type> params, String outName, Type outType, boolean outDeclaredInside) {}

    private static Plan plan(MethodDeclaration m, List<Statement> st, int from, int to) {
        List<Statement> seg = st.subList(from, to + 1);

        // 1. запрещённые конструкции
        for (Statement s : seg) {
            if (!s.findAll(YieldStmt.class).isEmpty() || !s.findAll(LocalClassDeclarationStmt.class).isEmpty()) return null;
            for (ReturnStmt r : s.findAll(ReturnStmt.class)) if (!insideNested(r, s)) return null;
            for (BreakStmt b : s.findAll(BreakStmt.class)) {
                if (b.getLabel().isPresent()) return null;
                if (!hasAncestor(b, s, true)) return null;
            }
            for (ContinueStmt c : s.findAll(ContinueStmt.class)) {
                if (c.getLabel().isPresent()) return null;
                if (!hasAncestor(c, s, false)) return null;
            }
        }

        // 2. переменные, видимые до серии (параметры + объявления верхнего уровня до неё), в порядке объявления
        Map<String, Type> outer = new LinkedHashMap<>();
        Set<String> uninit = new HashSet<>();
        for (Parameter p : m.getParameters()) outer.put(p.getNameAsString(), p.getType());
        for (int k = 0; k < from; k++) {
            if (st.get(k) instanceof ExpressionStmt es && es.getExpression() instanceof VariableDeclarationExpr vd) {
                for (VariableDeclarator d : vd.getVariables()) {
                    outer.put(d.getNameAsString(), d.getType());
                    if (d.getInitializer().isEmpty()) uninit.add(d.getNameAsString());
                }
            }
        }

        // 3. что серия читает/пишет
        Set<String> used = new HashSet<>();
        Set<String> assigned = new HashSet<>();
        for (Statement s : seg) {
            for (NameExpr n : s.findAll(NameExpr.class)) used.add(n.getNameAsString());
            for (AssignExpr a : s.findAll(AssignExpr.class)) {
                if (a.getTarget() instanceof NameExpr n) assigned.add(n.getNameAsString());
            }
            for (UnaryExpr u : s.findAll(UnaryExpr.class)) {
                if (u.getExpression() instanceof NameExpr n && isIncDec(u)) assigned.add(n.getNameAsString());
            }
        }
        Map<String, Type> params = new LinkedHashMap<>();
        for (Map.Entry<String, Type> e : outer.entrySet()) {
            if (!used.contains(e.getKey())) continue;
            if (uninit.contains(e.getKey()) || e.getValue().isVarType()) return null;
            params.put(e.getKey(), e.getValue());
        }

        // 4. что нужно после серии
        Set<String> usedAfter = new HashSet<>();
        for (int k = to + 1; k < st.size(); k++) {
            for (NameExpr n : st.get(k).findAll(NameExpr.class)) usedAfter.add(n.getNameAsString());
        }
        Map<String, Type> declaredInside = new LinkedHashMap<>();
        for (Statement s : seg) {
            if (s instanceof ExpressionStmt es && es.getExpression() instanceof VariableDeclarationExpr vd) {
                for (VariableDeclarator d : vd.getVariables()) {
                    if (usedAfter.contains(d.getNameAsString())) {
                        if (vd.getVariables().size() != 1 || d.getType().isVarType()) return null;
                        declaredInside.put(d.getNameAsString(), d.getType());
                    }
                }
            }
        }
        List<String> outs = new ArrayList<>();
        for (String a : assigned) if (params.containsKey(a) && usedAfter.contains(a)) outs.add(a);
        outs.addAll(declaredInside.keySet());
        if (outs.size() > 1) return null;
        // переменная, объявленная в серии не на верхнем уровне (например, в for) и нужная после, невозможна — Java не пустит.
        if (outs.isEmpty()) return new Plan(from, to, params, null, null, false);
        String o = outs.get(0);
        boolean inside = declaredInside.containsKey(o);
        return new Plan(from, to, params, o, inside ? declaredInside.get(o) : params.get(o), inside);
    }

    // ---------------------------------------------------------------- building

    private static String build(MethodDeclaration m, BlockStmt body, List<Statement> st, Plan p) {
        TypeDeclaration<?> owner = (TypeDeclaration<?>) m.getParentNode().get();
        Set<String> names = owner.getMethods().stream().map(MethodDeclaration::getNameAsString).collect(Collectors.toSet());
        String base = m.getNameAsString() + "Part";
        String name = base + 1;
        for (int k = 2; names.contains(name); k++) name = base + k;

        String ret = p.outType() == null ? "void" : p.outType().toString();
        String args = String.join(", ", p.params().keySet());
        String decl = p.params().entrySet().stream().map(e -> e.getValue() + " " + e.getKey()).collect(Collectors.joining(", "));
        String typeParams = m.getTypeParameters().isEmpty() ? "" : "<" + m.getTypeParameters().stream()
                .map(Node::toString).collect(Collectors.joining(", ")) + "> ";
        String throwsClause = m.getThrownExceptions().isEmpty() ? "" : " throws " + m.getThrownExceptions().stream()
                .map(Node::toString).collect(Collectors.joining(", "));
        String mods = m.isStatic() ? "private static " : "private ";
        String header = mods + typeParams + ret + " " + name + "(" + decl + ")" + throwsClause + " { }";

        JavaParser parser = new JavaParser();
        ParseResult<BodyDeclaration<?>> pr = parser.parseBodyDeclaration(header);
        if (!pr.isSuccessful() || pr.getResult().isEmpty()) return failure();
        MethodDeclaration helper = (MethodDeclaration) pr.getResult().get();
        BlockStmt hb = helper.getBody().get();
        int firstLine = st.get(p.from()).getBegin().map(b -> b.line).orElse(0);
        int lastLine = st.get(p.to()).getEnd().map(e -> e.line).orElse(0);
        for (int k = p.from(); k <= p.to(); k++) hb.addStatement(st.get(k).clone());
        if (p.outName() != null) hb.addStatement(new ReturnStmt(new NameExpr(p.outName())));

        String call = name + "(" + args + ")";
        String callStmt;
        if (p.outName() == null) callStmt = call + ";";
        else if (p.outDeclaredInside()) callStmt = p.outType() + " " + p.outName() + " = " + call + ";";
        else callStmt = p.outName() + " = " + call + ";";
        Statement replacement = parser.parseStatement(callStmt).getResult().orElse(null);
        if (replacement == null) return failure();

        for (int k = p.to(); k >= p.from(); k--) body.getStatements().remove(st.get(k));
        body.getStatements().add(p.from(), replacement);
        int idx = owner.getMembers().indexOf(m);
        owner.getMembers().add(idx + 1, helper);
        return "Выделен метод «" + name + "» из «" + m.getNameAsString() + "» (строки " + firstLine + "–" + lastLine + ")"
                + (p.params().isEmpty() ? "" : ", параметры: " + args)
                + (p.outName() == null ? "" : ", возвращает «" + p.outName() + "»") + ".";
    }

    private static String failure() {
        throw new IllegalStateException("Не удалось собрать выделенный метод");
    }

    // ---------------------------------------------------------------- helpers

    private static MethodDeclaration findMethod(CompilationUnit cu, int line) {
        MethodDeclaration best = null;
        for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
            if (m.getRange().isEmpty()) continue;
            int b = m.getRange().get().begin.line;
            int e = m.getRange().get().end.line;
            if (line < b || line > e) continue;
            if (best == null || span(m) < span(best)) best = m;
        }
        return best;
    }

    private static int span(Node n) {
        return n.getRange().map(r -> r.end.line - r.begin.line + 1).orElse(0);
    }

    private static int span(Node first, Node last) {
        if (first.getBegin().isEmpty() || last.getEnd().isEmpty()) return 0;
        return last.getEnd().get().line - first.getBegin().get().line + 1;
    }

    private static boolean isIncDec(UnaryExpr u) {
        return switch (u.getOperator()) {
            case PREFIX_INCREMENT, PREFIX_DECREMENT, POSTFIX_INCREMENT, POSTFIX_DECREMENT -> true;
            default -> false;
        };
    }

    /** return внутри лямбды/анонимного класса внутри серии не выходит из метода. */
    private static boolean insideNested(Node n, Node root) {
        for (Node c = n.getParentNode().orElse(null); c != null && c != root; c = c.getParentNode().orElse(null)) {
            if (c instanceof LambdaExpr || c instanceof ObjectCreationExpr) return true;
        }
        return false;
    }

    /** break (inSwitchToo) / continue замкнуты внутри серии: есть охватывающий цикл (или switch) в пределах серии. */
    private static boolean hasAncestor(Node n, Node root, boolean inSwitchToo) {
        for (Node c = n.getParentNode().orElse(null); c != null; c = c.getParentNode().orElse(null)) {
            if (c instanceof ForStmt || c instanceof ForEachStmt || c instanceof WhileStmt || c instanceof DoStmt) return true;
            if (inSwitchToo && c instanceof SwitchStmt) return true;
            if (c == root) return false;
        }
        return false;
    }
}

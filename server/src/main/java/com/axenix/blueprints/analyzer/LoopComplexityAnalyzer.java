package com.axenix.blueprints.analyzer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Эвристическая оценка Big-O по вложенности циклов.
 * <p>
 * Правила:
 *  - цикл с константной границей (i &lt; 10, foreach по литералу массива/List.of) в степень не входит;
 *  - степень = число "линейных" циклов вдоль самой глубокой ветки вложенности;
 *  - вызов contains/indexOf/remove на List внутри линейного цикла добавляет ещё один уровень;
 *  - get(i) на LinkedList внутри цикла тоже даёт +1 уровень.
 * <p>
 * Ограничения (честно указать в описании решения): не учитывает static final-константы как границы,
 * не разворачивает вызовы пользовательских методов, типы берёт из объявлений (без SymbolSolver).
 */
public class LoopComplexityAnalyzer {

    public enum Type { NESTED_LOOP, COSTLY_CALL_IN_LOOP }

    public record Finding(Type type, String method, int degree, String complexity,
                          int startLine, int endLine, String message) {}

    private static final Set<String> LIST_TYPES = Set.of("List", "ArrayList", "LinkedList");
    private static final Set<String> SCAN_METHODS = Set.of("contains", "indexOf", "lastIndexOf", "remove", "containsAll");
    private static final Set<String> CONSTANT_FACTORIES = Set.of("of", "asList");

    public List<Finding> analyze(CompilationUnit cu) {
        Map<String, String> listVars = collectListVariables(cu);
        List<Finding> out = new ArrayList<>();
        for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
            m.getBody().ifPresent(body -> body.accept(new MethodVisitor(m.getNameAsString(), listVars, out), null));
        }
        for (ConstructorDeclaration c : cu.findAll(ConstructorDeclaration.class)) {
            c.getBody().accept(new MethodVisitor(c.getNameAsString(), listVars, out), null);
        }
        return out;
    }

    public static String complexityLabel(int degree) {
        return switch (degree) {
            case 0 -> "O(1)";
            case 1 -> "O(n)";
            case 2 -> "O(n²)";
            case 3 -> "O(n³)";
            default -> "O(n^" + degree + ")";
        };
    }

    /** имя переменной → объявленный тип (List/ArrayList/LinkedList), по полям, локальным и параметрам. */
    private static Map<String, String> collectListVariables(CompilationUnit cu) {
        Map<String, String> map = new HashMap<>();
        for (VariableDeclarator v : cu.findAll(VariableDeclarator.class)) {
            if (v.getType() instanceof ClassOrInterfaceType t && LIST_TYPES.contains(t.getNameAsString())) {
                map.put(v.getNameAsString(), t.getNameAsString());
            }
        }
        for (Parameter p : cu.findAll(Parameter.class)) {
            if (p.getType() instanceof ClassOrInterfaceType t && LIST_TYPES.contains(t.getNameAsString())) {
                map.put(p.getNameAsString(), t.getNameAsString());
            }
        }
        return map;
    }

    private static final class MethodVisitor extends VoidVisitorAdapter<Void> {

        /** Один активный цикл на стеке. maxInner = максимальная степень среди вложенных циклов. */
        private static final class Frame {
            final boolean constant;
            int maxInner = 0;
            Frame(boolean constant) { this.constant = constant; }
        }

        private final String method;
        private final Map<String, String> listVars;
        private final List<Finding> out;
        private final Deque<Frame> stack = new ArrayDeque<>();
        private int linearDepth = 0; // сколько линейных циклов вокруг текущей точки

        MethodVisitor(String method, Map<String, String> listVars, List<Finding> out) {
            this.method = method;
            this.listVars = listVars;
            this.out = out;
        }

        @Override public void visit(ForStmt n, Void a) {
            loop(n, hasConstantBound(n), () -> super.visit(n, a));
        }

        @Override public void visit(ForEachStmt n, Void a) {
            loop(n, isConstantIterable(n.getIterable()), () -> super.visit(n, a));
        }

        @Override public void visit(WhileStmt n, Void a) {
            loop(n, false, () -> super.visit(n, a)); // консервативно: считаем линейным
        }

        @Override public void visit(DoStmt n, Void a) {
            loop(n, false, () -> super.visit(n, a));
        }

        @Override public void visit(MethodCallExpr c, Void a) {
            super.visit(c, a);
            if (linearDepth == 0) return;
            c.getScope()
                    .filter(Expression::isNameExpr)
                    .map(s -> s.asNameExpr().getNameAsString())
                    .ifPresent(var -> {
                        String type = listVars.get(var);
                        if (type == null) return;
                        String name = c.getNameAsString();
                        boolean costly = SCAN_METHODS.contains(name)
                                || ("LinkedList".equals(type) && name.equals("get"));
                        if (!costly) return;
                        int degree = linearDepth + 1;
                        out.add(new Finding(Type.COSTLY_CALL_IN_LOOP, method, degree, complexityLabel(degree),
                                line(c, true), line(c, false),
                                var + "." + name + "() — линейный проход по " + type + " внутри цикла"));
                    });
        }

        private void loop(Node n, boolean constant, Runnable walk) {
            Frame f = new Frame(constant);
            stack.push(f);
            if (!constant) linearDepth++;
            walk.run();
            if (!constant) linearDepth--;
            stack.pop();

            int total = (constant ? 0 : 1) + f.maxInner;
            if (stack.isEmpty()) {
                // внешний цикл: сообщаем один раз, с самой глубокой вложенностью
                if (total >= 2) {
                    out.add(new Finding(Type.NESTED_LOOP, method, total, complexityLabel(total),
                            line(n, true), line(n, false),
                            "Вложенность линейных циклов: " + total + " уровня → " + complexityLabel(total)));
                }
            } else {
                Frame parent = stack.peek();
                parent.maxInner = Math.max(parent.maxInner, total);
            }
        }

        /** A loop is constant only when its bound is a literal and the update moves toward it. */
        private static boolean hasConstantBound(ForStmt f) {
            if (f.getCompare().isEmpty() || f.getUpdate().size() != 1 || f.getInitialization().isEmpty()) return false;
            if (!(f.getCompare().get() instanceof BinaryExpr b) || !isComparison(b.getOperator())) return false;
            String init = f.getInitialization().get(0).toString().trim();
            int equals = init.indexOf('=');
            if (equals < 0) return false;
            if (!init.substring(equals + 1).trim().matches("-?[0-9_]+[lL]?")) return false;
            String variable = init.substring(0, equals).trim();
            variable = variable.substring(variable.lastIndexOf(' ') + 1);
            String update = f.getUpdate().get(0).toString();
            boolean increment = update.equals(variable + "++") || update.equals("++" + variable);
            boolean decrement = update.equals(variable + "--") || update.equals("--" + variable);
            if (!increment && !decrement) return false;
            boolean rightLiteral = b.getRight().isLiteralExpr() && b.getLeft().toString().equals(variable);
            boolean leftLiteral = b.getLeft().isLiteralExpr() && b.getRight().toString().equals(variable);
            return (increment && (rightLiteral && (b.getOperator() == BinaryExpr.Operator.LESS || b.getOperator() == BinaryExpr.Operator.LESS_EQUALS)
                    || leftLiteral && (b.getOperator() == BinaryExpr.Operator.GREATER || b.getOperator() == BinaryExpr.Operator.GREATER_EQUALS)))
                    || (decrement && (rightLiteral && (b.getOperator() == BinaryExpr.Operator.GREATER || b.getOperator() == BinaryExpr.Operator.GREATER_EQUALS)
                    || leftLiteral && (b.getOperator() == BinaryExpr.Operator.LESS || b.getOperator() == BinaryExpr.Operator.LESS_EQUALS)));
        }
        private static boolean isComparison(BinaryExpr.Operator op) {
            return switch (op) {
                case LESS, LESS_EQUALS, GREATER, GREATER_EQUALS, NOT_EQUALS -> true;
                default -> false;
            };
        }

        /** foreach по new int[]{...}, List.of(...), Arrays.asList(...). */
        private static boolean isConstantIterable(Expression e) {
            if (e instanceof ArrayCreationExpr ac) return ac.getInitializer().isPresent();
            if (e instanceof MethodCallExpr mc) {
                return CONSTANT_FACTORIES.contains(mc.getNameAsString())
                        && mc.getScope().map(s -> s.toString().equals("List") || s.toString().equals("Arrays")
                        || s.toString().equals("Set")).orElse(false);
            }
            return false;
        }

        private static int line(Node n, boolean begin) {
            return n.getRange().map(r -> begin ? r.begin.line : r.end.line).orElse(-1);
        }
    }
}

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
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.TryStmt;
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
import java.util.Comparator;
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
        int methodSpan = span(m);
        // Сначала операторы верхнего уровня; если подходящей серии нет (весь метод — один большой цикл/try),
        // спускаемся во вложенные блоки (тела циклов, if, try), самые крупные первыми.
        List<BlockStmt> containers = new ArrayList<>();
        containers.add(body);
        body.findAll(BlockStmt.class).stream()
                .filter(b -> b != body && span(b) >= MIN_SEGMENT_LINES + 2 && isPlainNested(b, m))
                .sorted(Comparator.comparingInt((BlockStmt b) -> span(b)).reversed())
                .forEach(containers::add);
        for (BlockStmt c : containers) {
            List<Statement> st = new ArrayList<>(c.getStatements());
            // серии без возврата значений или с одним значением предпочтительнее: класс-результат — запасной вариант
            Plan best = null;
            long bestScore = -1;
            Plan bestMulti = null;
            long bestMultiScore = -1;
            for (int i = 0; i < st.size(); i++) {
                for (int j = i; j < st.size(); j++) {
                    int seg = span(st.get(i), st.get(j));
                    if (seg < MIN_SEGMENT_LINES || seg >= methodSpan - 2) continue;
                    // во вложенном блоке вынос ВСЕГО содержимого лишь переносит сложность в другой метод
                    if (c != body && i == 0 && j == st.size() - 1) continue;
                    long score = Math.min(seg, methodSpan - seg);
                    if (score <= bestScore && score <= bestMultiScore) continue;
                    Plan p = plan(m, c, st, i, j);
                    if (p == null) continue;
                    if (p.outs().size() > 1) {
                        if (score > bestMultiScore) {
                            bestMulti = p;
                            bestMultiScore = score;
                        }
                    } else if (score > bestScore) {
                        best = p;
                        bestScore = score;
                    }
                }
            }
            if (best == null) best = bestMulti;
            if (best != null) return Optional.of(build(m, c, st, best));
        }
        return Optional.empty();
    }

    /** Вложенный блок не внутри лямбды, анонимного/локального класса и не в ветке switch (там сложнее область видимости). */
    private static boolean isPlainNested(BlockStmt b, MethodDeclaration m) {
        for (Node c = b.getParentNode().orElse(null); c != null && c != m; c = c.getParentNode().orElse(null)) {
            if (c instanceof LambdaExpr || c instanceof ObjectCreationExpr || c instanceof LocalClassDeclarationStmt
                    || c instanceof SwitchEntry) return false;
        }
        return true;
    }

    /** Переменные, видимые перед позицией {@code from} в блоке {@code container}: параметры метода и объявления снаружи. */
    private static Map<String, Type> visibleBefore(MethodDeclaration m, BlockStmt container, int from, Set<String> uninit) {
        List<Map.Entry<String, Type>> rev = new ArrayList<>();
        Node node = container;
        int upTo = from;
        while (node != null && node != m) {
            if (node instanceof BlockStmt blk) {
                List<Statement> sts = blk.getStatements();
                for (int k = Math.min(upTo, sts.size()) - 1; k >= 0; k--) {
                    if (sts.get(k) instanceof ExpressionStmt es && es.getExpression() instanceof VariableDeclarationExpr vd) {
                        for (int q = vd.getVariables().size() - 1; q >= 0; q--) {
                            VariableDeclarator d = vd.getVariable(q);
                            rev.add(Map.entry(d.getNameAsString(), d.getType()));
                            if (d.getInitializer().isEmpty()) uninit.add(d.getNameAsString());
                        }
                    }
                }
            }
            Node parent = node.getParentNode().orElse(null);
            if (parent instanceof ForStmt f) {
                for (var init : f.getInitialization()) {
                    if (init instanceof VariableDeclarationExpr vd) {
                        for (VariableDeclarator d : vd.getVariables()) rev.add(Map.entry(d.getNameAsString(), d.getType()));
                    }
                }
            } else if (parent instanceof ForEachStmt fe) {
                for (VariableDeclarator d : fe.getVariable().getVariables()) rev.add(Map.entry(d.getNameAsString(), d.getType()));
            } else if (parent instanceof CatchClause cc) {
                rev.add(Map.entry(cc.getParameter().getNameAsString(), cc.getParameter().getType()));
            } else if (parent instanceof TryStmt ts && ts.getTryBlock() == node) {
                for (var r : ts.getResources()) {
                    if (r instanceof VariableDeclarationExpr vd) {
                        for (VariableDeclarator d : vd.getVariables()) rev.add(Map.entry(d.getNameAsString(), d.getType()));
                    }
                }
            }
            if (parent instanceof BlockStmt pb) upTo = pb.getStatements().indexOf(node);
            node = parent;
        }
        Map<String, Type> outer = new LinkedHashMap<>();
        for (Parameter pr : m.getParameters()) outer.put(pr.getNameAsString(), pr.getType());
        for (int k = rev.size() - 1; k >= 0; k--) outer.put(rev.get(k).getKey(), rev.get(k).getValue());
        return outer;
    }

    // ---------------------------------------------------------------- planning

    /** outs — значения, нужные после серии (имя → тип); inside — какие из них объявлены внутри серии. */
    private record Plan(int from, int to, Map<String, Type> params, Map<String, Type> outs, Set<String> inside) {}

    private static final int MAX_OUTS = 10;

    private static Plan plan(MethodDeclaration m, BlockStmt container, List<Statement> st, int from, int to) {
        boolean topLevel = container == m.getBody().orElse(null);
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
        Set<String> uninit = new HashSet<>();
        Map<String, Type> outer = visibleBefore(m, container, from, uninit);

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
        // Во вложенном блоке (например, теле цикла) значение внешней переменной может понадобиться и вне серии:
        // на следующей итерации или после цикла — считаем «нужным после» любое упоминание вне серии.
        Set<String> usedOutside = new HashSet<>(usedAfter);
        if (!topLevel) {
            for (NameExpr n : m.findAll(NameExpr.class)) {
                boolean inside = false;
                for (Statement s : seg) if (isDescendant(n, s)) inside = true;
                if (!inside) usedOutside.add(n.getNameAsString());
            }
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
        Map<String, Type> outs = new LinkedHashMap<>();
        Set<String> inside = new HashSet<>();
        for (String a : params.keySet()) if (assigned.contains(a) && usedOutside.contains(a)) outs.put(a, params.get(a));
        for (Map.Entry<String, Type> e : declaredInside.entrySet()) {
            outs.put(e.getKey(), e.getValue());
            inside.add(e.getKey());
        }
        if (outs.size() > MAX_OUTS) return null;
        if (outs.size() > 1) {
            // несколько значений возвращаем через маленький класс-результат: только в обычном классе, без дженериков,
            // и вынос не должен быть почти всем методом (иначе новый метод сам станет длинным)
            TypeDeclaration<?> owner = (TypeDeclaration<?>) m.getParentNode().get();
            if (!(owner instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration cd) || cd.isInterface()) return null;
            if (!m.getTypeParameters().isEmpty()) return null;
            int segSpan = span(st.get(from), st.get(to));
            if (segSpan * 2 > span(m) + 2) return null;
            for (Type t : outs.values()) if (t.isVarType()) return null;
        }
        // переменная, объявленная в серии не на верхнем уровне (например, в for) и нужная после, невозможна — Java не пустит.
        return new Plan(from, to, params, outs, inside);
    }

    // ---------------------------------------------------------------- building

    private static String build(MethodDeclaration m, BlockStmt body, List<Statement> st, Plan p) {
        // body — блок, из которого вырезается серия (тело метода или вложенный блок)
        TypeDeclaration<?> owner = (TypeDeclaration<?>) m.getParentNode().get();
        Set<String> names = owner.getMethods().stream().map(MethodDeclaration::getNameAsString).collect(Collectors.toSet());
        String base = m.getNameAsString().replaceAll("Part\\d+$", "") + "Part";
        String name = base + 1;
        for (int k = 2; names.contains(name); k++) name = base + k;

        boolean multi = p.outs().size() > 1;
        String holder = null;
        if (multi) {
            Set<String> members = owner.getMembers().stream()
                    .filter(x -> x instanceof TypeDeclaration<?>).map(x -> ((TypeDeclaration<?>) x).getNameAsString())
                    .collect(Collectors.toSet());
            String hbase = Character.toUpperCase(name.charAt(0)) + name.substring(1) + "Result";
            holder = hbase;
            for (int k = 2; members.contains(holder); k++) holder = hbase + k;
        }
        String ret = p.outs().isEmpty() ? "void" : multi ? holder : p.outs().values().iterator().next().toString();
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
        BodyDeclaration<?> holderClass = null;
        if (multi) {
            String fields = p.outs().entrySet().stream().map(e -> "final " + e.getValue() + " " + e.getKey() + ";")
                    .collect(Collectors.joining(" "));
            String ctorParams = p.outs().entrySet().stream().map(e -> e.getValue() + " " + e.getKey())
                    .collect(Collectors.joining(", "));
            String assigns = p.outs().keySet().stream().map(n -> "this." + n + " = " + n + ";").collect(Collectors.joining(" "));
            ParseResult<BodyDeclaration<?>> hr = parser.parseBodyDeclaration("private static final class " + holder + " { "
                    + fields + " " + holder + "(" + ctorParams + ") { " + assigns + " } }");
            if (!hr.isSuccessful() || hr.getResult().isEmpty()) return failure();
            holderClass = hr.getResult().get();
            hb.addStatement(new ReturnStmt(parser.parseExpression("new " + holder + "(" + String.join(", ", p.outs().keySet()) + ")")
                    .getResult().orElseThrow()));
        } else if (!p.outs().isEmpty()) {
            hb.addStatement(new ReturnStmt(new NameExpr(p.outs().keySet().iterator().next())));
        }

        String call = name + "(" + args + ")";
        List<Statement> replacement = new ArrayList<>();
        if (p.outs().isEmpty()) {
            replacement.add(parser.parseStatement(call + ";").getResult().orElse(null));
        } else if (!multi) {
            String o = p.outs().keySet().iterator().next();
            replacement.add(parser.parseStatement((p.inside().contains(o) ? p.outs().get(o) + " " : "") + o + " = " + call + ";")
                    .getResult().orElse(null));
        } else {
            Set<String> used = m.findAll(com.github.javaparser.ast.expr.SimpleName.class).stream()
                    .map(com.github.javaparser.ast.expr.SimpleName::asString).collect(Collectors.toSet());
            String res = "result";
            for (int k = 2; used.contains(res); k++) res = "result" + k;
            replacement.add(parser.parseStatement(holder + " " + res + " = " + call + ";").getResult().orElse(null));
            for (Map.Entry<String, Type> e : p.outs().entrySet()) {
                String pre = p.inside().contains(e.getKey()) ? e.getValue() + " " : "";
                replacement.add(parser.parseStatement(pre + e.getKey() + " = " + res + "." + e.getKey() + ";")
                        .getResult().orElse(null));
            }
        }
        if (replacement.stream().anyMatch(java.util.Objects::isNull)) return failure();

        for (int k = p.to(); k >= p.from(); k--) body.getStatements().remove(st.get(k));
        for (int k = 0; k < replacement.size(); k++) body.getStatements().add(p.from() + k, replacement.get(k));
        int idx = owner.getMembers().indexOf(m);
        owner.getMembers().add(idx + 1, helper);
        if (holderClass != null) owner.getMembers().add(idx + 2, holderClass);
        String outsText = p.outs().isEmpty() ? "" : multi
                ? ", возвращает " + p.outs().size() + " значений через класс «" + holder + "»"
                : ", возвращает «" + p.outs().keySet().iterator().next() + "»";
        return "Выделен метод «" + name + "» из «" + m.getNameAsString() + "» (строки " + firstLine + "–" + lastLine + ")"
                + (p.params().isEmpty() ? "" : ", параметры: " + args) + outsText + ".";
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

    private static boolean isDescendant(Node n, Node root) {
        for (Node c = n; c != null; c = c.getParentNode().orElse(null)) if (c == root) return true;
        return false;
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

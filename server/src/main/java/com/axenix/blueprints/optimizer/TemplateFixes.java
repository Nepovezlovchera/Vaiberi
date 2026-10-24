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
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.util.HashSet;
import java.util.List;
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
        if (r.isEmpty()) r = duplicatePairsToSet(cu, line);
        if (r.isEmpty()) r = nestedLoopToMap(cu, line);
        return r.isPresent() ? r : indexedPairsToMap(cu, line);
    }

    /**
     * Поиск одинаковых ключей во вложенном цикле с условием вида
     * {@code a != b && a.key.equals(b.key)}. Два линейных прохода через Set
     * сохраняют порядок исходного внешнего цикла: сначала собираются ключи,
     * которые встречаются повторно, затем они добавляются в результат при
     * первом появлении во внешнем порядке.
     */
    public static Optional<String> duplicatePairsToSet(CompilationUnit cu, int line) {
        ForEachStmt outer = null;
        for (ForEachStmt f : cu.findAll(ForEachStmt.class)) {
            if (f.getRange().map(r -> r.begin.line).orElse(-1) == line) {
                outer = f;
                break;
            }
        }
        if (outer == null || !(outer.getParentNode().orElse(null) instanceof BlockStmt outerBlock)
                || !(outer.getBody() instanceof BlockStmt outerBody)) return Optional.empty();
        Statement onlyOuter = outerBody.getStatements().size() == 1 ? outerBody.getStatement(0) : null;
        if (!(onlyOuter instanceof ForEachStmt inner) || !(inner.getBody() instanceof BlockStmt innerBody)) return Optional.empty();
        Statement onlyInner = innerBody.getStatements().size() == 1 ? innerBody.getStatement(0) : null;
        if (!(onlyInner instanceof IfStmt match) || match.getElseStmt().isPresent()) return Optional.empty();
        if (!(match.getCondition() instanceof BinaryExpr and) || and.getOperator() != BinaryExpr.Operator.AND) return Optional.empty();

        String a = outer.getVariable().getVariable(0).getNameAsString();
        String b = inner.getVariable().getVariable(0).getNameAsString();
        Expression keyA = null;
        Expression keyB = null;
        boolean distinct = false;
        for (Expression part : List.of(and.getLeft(), and.getRight())) {
            if (part instanceof BinaryExpr neq && neq.getOperator() == BinaryExpr.Operator.NOT_EQUALS) {
                String left = neq.getLeft().toString();
                String right = neq.getRight().toString();
                distinct = (left.equals(a) && right.equals(b)) || (left.equals(b) && right.equals(a));
            }
            if (part instanceof MethodCallExpr eq && eq.getNameAsString().equals("equals")
                    && eq.getScope().isPresent() && eq.getArguments().size() == 1) {
                Expression left = eq.getScope().get();
                Expression right = eq.getArgument(0);
                if (refs(left, a) && !refs(left, b) && refs(right, b) && !refs(right, a)) {
                    keyA = left; keyB = right;
                } else if (refs(right, a) && !refs(right, b) && refs(left, b) && !refs(left, a)) {
                    keyA = right; keyB = left;
                }
            }
        }
        if (!distinct || keyA == null || keyB == null || !inner.getIterable().toString().equals(outer.getIterable().toString())) {
            return Optional.empty();
        }
        CallableDeclaration<?> method = outer.findAncestor(CallableDeclaration.class).orElse(null);
        if (method == null) return Optional.empty();

        // Внутри условия должен быть ровно шаблон
        // if (!duplicates.contains(key)) { duplicates.add(key); }.
        Statement guardStatement = match.getThenStmt() instanceof BlockStmt block
                && block.getStatements().size() == 1 ? block.getStatement(0) : null;
        if (!(guardStatement instanceof IfStmt guard) || guard.getElseStmt().isPresent()
                || !(guard.getCondition() instanceof UnaryExpr neg)
                || neg.getOperator() != UnaryExpr.Operator.LOGICAL_COMPLEMENT
                || !(neg.getExpression() instanceof MethodCallExpr contains)
                || !contains.getNameAsString().equals("contains") || contains.getArguments().size() != 1
                || contains.getScope().filter(Expression::isNameExpr).isEmpty()) return Optional.empty();
        Statement addStatement = guard.getThenStmt() instanceof BlockStmt block
                && block.getStatements().size() == 1 ? block.getStatement(0) : guard.getThenStmt();
        MethodCallExpr add = addStatement instanceof ExpressionStmt es
                && es.getExpression() instanceof MethodCallExpr mc ? mc : null;
        if (add == null || !add.getNameAsString().equals("add") || add.getArguments().size() != 1
                || add.getScope().filter(Expression::isNameExpr).isEmpty()) return Optional.empty();
        if (contains == null || add == null || !contains.getScope().get().toString().equals(add.getScope().get().toString())
                || !contains.getArgument(0).toString().equals(add.getArgument(0).toString())) return Optional.empty();
        String result = contains.getScope().get().toString();
        String src = outer.getIterable().toString();
        String elem = outer.getVariable().getCommonType().toString();
        String item = freshName(method, a);
        String seen = freshName(method, "seenKeys");
        String duplicate = freshName(method, "duplicateKeys");
        String emitted = freshName(method, "emittedKeys");
        String firstKey = replaceIdentifier(keyA.toString(), a, item);
        JavaParser parser = new JavaParser();
        Statement seenDecl = parseStmt(parser, "Set<Object> " + seen + " = new HashSet<>();");
        Statement dupDecl = parseStmt(parser, "Set<Object> " + duplicate + " = new HashSet<>();");
        Statement emitDecl = parseStmt(parser, "Set<Object> " + emitted + " = new HashSet<>();");
        Statement scan = parseStmt(parser, "for (" + elem + " " + item + " : " + src + ") { if (!" + seen
                + ".add(" + firstKey + ")) " + duplicate + ".add(" + firstKey + "); }");
        Statement emit = parseStmt(parser, "for (" + elem + " " + item + " : " + src + ") { if (" + duplicate
                + ".contains(" + firstKey + ") && " + emitted + ".add(" + firstKey + ")) " + result + ".add(" + firstKey + "); }");
        if (seenDecl == null || dupDecl == null || emitDecl == null || scan == null || emit == null) return Optional.empty();
        int at = outerBlock.getStatements().indexOf(outer);
        outerBlock.getStatements().add(at, seenDecl);
        outerBlock.getStatements().add(at + 1, dupDecl);
        outerBlock.getStatements().add(at + 2, scan);
        outerBlock.getStatements().add(at + 3, emitDecl);
        outerBlock.getStatements().add(at + 4, emit);
        outerBlock.getStatements().remove(outer);
        ensureImport(cu, "java.util.Set");
        ensureImport(cu, "java.util.HashSet");
        return Optional.of("Вложенный поиск повторяющихся ключей заменён линейными проходами через HashSet: O(n²) → O(n).");
    }

    private static String replaceIdentifier(String text, String from, String to) {
        return text.replaceAll("\\b" + java.util.regex.Pattern.quote(from) + "\\b", to);
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

    /**
     * Индексные вложенные циклы с сопоставлением по ключу:
     * <pre>
     * for (int i = 0; i &lt; n; i++) { for (int j = i + 1; j &lt; n; j++) { if (X.get(i).k.equals(X.get(j).k)) BODY } }
     * </pre>
     * Индексы позиций по ключу строятся один раз перед внешним циклом; внутри остаются только позиции с тем же ключом
     * (по возрастанию, как в исходном цикле), так что порядок и число срабатываний BODY сохраняются, а i и j —
     * настоящие индексы. Начало внутреннего цикла: {@code i + 1} или {@code 0}. Сравнение {@code ==} допустимо только
     * для примитивных полей/элементов, тип которых виден в файле. Коллекция не должна меняться во внешнем цикле.
     */
    public static Optional<String> indexedPairsToMap(CompilationUnit cu, int line) {
        ForStmt outer = null;
        for (ForStmt f : cu.findAll(ForStmt.class)) {
            if (f.getRange().map(r -> r.begin.line).orElse(-1) == line) {
                outer = f;
                break;
            }
        }
        if (outer == null || !(outer.getParentNode().orElse(null) instanceof BlockStmt outerBlock)) return Optional.empty();
        CallableDeclaration<?> method = outer.findAncestor(CallableDeclaration.class).orElse(null);
        if (method == null) return Optional.empty();
        String i = loopVar(outer, false);
        String bound = loopBound(outer, i);
        if (i == null || bound == null) return Optional.empty();

        Statement ob = outer.getBody();
        Statement onlyOuter = ob instanceof BlockStmt bb ? (bb.getStatements().size() == 1 ? bb.getStatement(0) : null) : ob;
        if (!(onlyOuter instanceof ForStmt inner)) return Optional.empty();
        String j = loopVar(inner, true);
        String startKind = j == null ? null : innerStart(inner, i);
        if (j == null || startKind == null || j.equals(i)) return Optional.empty();
        if (!bound.equals(loopBound(inner, j))) return Optional.empty();

        Statement ib = inner.getBody();
        Statement only = ib instanceof BlockStmt blk ? (blk.getStatements().size() == 1 ? blk.getStatement(0) : null) : ib;
        if (!(only instanceof IfStmt iff) || iff.getElseStmt().isPresent()) return Optional.empty();
        Expression s1;
        Expression s2;
        boolean viaEquals;
        if (iff.getCondition() instanceof MethodCallExpr eq && eq.getNameAsString().equals("equals")
                && eq.getScope().isPresent() && eq.getArguments().size() == 1) {
            s1 = eq.getScope().get();
            s2 = eq.getArgument(0);
            viaEquals = true;
        } else if (iff.getCondition() instanceof com.github.javaparser.ast.expr.BinaryExpr be
                && be.getOperator() == com.github.javaparser.ast.expr.BinaryExpr.Operator.EQUALS) {
            s1 = be.getLeft();
            s2 = be.getRight();
            viaEquals = false;
        } else {
            return Optional.empty();
        }
        Expression keyI;
        Expression keyJ;
        if (refs(s1, i) && !refs(s1, j) && refs(s2, j) && !refs(s2, i)) {
            keyI = s1;
            keyJ = s2;
        } else if (refs(s2, i) && !refs(s2, j) && refs(s1, j) && !refs(s1, i)) {
            keyI = s2;
            keyJ = s1;
        } else {
            return Optional.empty();
        }
        // ключи зависят только от индекса и одной коллекции X (X.get(idx) или X[idx])
        String coll = null;
        for (Expression k : new Expression[] {keyI, keyJ}) {
            String idx = k == keyI ? i : j;
            for (NameExpr n : k.findAll(NameExpr.class)) {
                String nm = n.getNameAsString();
                if (nm.equals(idx)) continue;
                if (coll == null) coll = nm;
                else if (!coll.equals(nm)) return Optional.empty();
            }
            for (MethodCallExpr mc : k.findAll(MethodCallExpr.class)) if (mc.getScope().isEmpty()) return Optional.empty();
        }
        if (coll == null) return Optional.empty();
        final String X = coll;
        if (!usesElementAccess(keyI, X, i) || !usesElementAccess(keyJ, X, j)) return Optional.empty();
        if (!viaEquals && !(isPrimitiveKey(cu, method, X, keyI, i) && isPrimitiveKey(cu, method, X, keyJ, j))) return Optional.empty();

        // коллекция, границы и индексы не меняются во внешнем цикле
        for (MethodCallExpr mc : outer.findAll(MethodCallExpr.class)) {
            if (mc.getScope().filter(x -> x.isNameExpr() && x.asNameExpr().getNameAsString().equals(X)).isPresent()
                    && MUTATORS.contains(mc.getNameAsString())) return Optional.empty();
            for (Expression arg : mc.getArguments()) {
                if (arg.isNameExpr() && arg.asNameExpr().getNameAsString().equals(X)) return Optional.empty();
            }
        }
        Set<String> frozen = new HashSet<>();
        frozen.add(X);
        frozen.add(j);
        for (NameExpr n : outer.getCompare().map(c -> c.findAll(NameExpr.class)).orElse(new java.util.ArrayList<>())) {
            frozen.add(n.getNameAsString());
        }
        for (AssignExpr as : iff.getThenStmt().findAll(AssignExpr.class)) {
            if (as.getTarget().isNameExpr() && (frozen.contains(as.getTarget().asNameExpr().getNameAsString())
                    || as.getTarget().asNameExpr().getNameAsString().equals(i))) return Optional.empty();
        }
        for (UnaryExpr u : iff.getThenStmt().findAll(UnaryExpr.class)) {
            boolean incDec = switch (u.getOperator()) {
                case PREFIX_INCREMENT, PREFIX_DECREMENT, POSTFIX_INCREMENT, POSTFIX_DECREMENT -> true;
                default -> false;
            };
            if (incDec && u.getExpression().isNameExpr()) {
                String nm = u.getExpression().asNameExpr().getNameAsString();
                if (frozen.contains(nm) || nm.equals(i)) return Optional.empty();
            }
        }

        String mapName = freshName(method, "posByKey");
        String keyParam = freshName(method, "key");
        String matches = freshName(method, "matches");
        JavaParser parser = new JavaParser();
        Statement mapDecl = parseStmt(parser, "Map<Object, List<Integer>> " + mapName + " = new HashMap<>();");
        Statement fill = parseStmt(parser, "for (int " + j + " = 0; " + j + " < " + bound + "; " + j + "++) { " + mapName
                + ".computeIfAbsent(" + keyJ + ", " + keyParam + " -> new ArrayList<>()).add(" + j + "); }");
        Statement guard = parseStmt(parser, "{ List<Integer> " + matches + " = " + mapName + ".get(" + keyI + "); if (" + matches
                + " != null) { for (int " + j + " : " + matches + ") { } } }");
        if (mapDecl == null || fill == null || guard == null) return Optional.empty();
        BlockStmt guardBlock = guard.asBlockStmt();
        ForEachStmt newInner = guardBlock.getStatement(1).asIfStmt().getThenStmt().asBlockStmt().getStatement(0).asForEachStmt();
        BlockStmt newBody = new BlockStmt();
        if (startKind.equals("next")) newBody.addStatement(parseStmt(parser, "if (" + j + " <= " + i + ") { continue; }"));
        newBody.addStatement(iff.getThenStmt().clone());
        newInner.setBody(newBody);
        BlockStmt outerBody = new BlockStmt();
        outerBody.addStatement(guardBlock.getStatement(0).clone());
        outerBody.addStatement(guardBlock.getStatement(1).clone());
        outerBody.getStatement(1).asIfStmt().getThenStmt().asBlockStmt().getStatement(0).asForEachStmt().setBody(newBody);
        outer.setBody(outerBody);
        int at = outerBlock.getStatements().indexOf(outer);
        outerBlock.getStatements().add(at, mapDecl);
        outerBlock.getStatements().add(at + 1, fill);
        ensureImport(cu, "java.util.Map");
        ensureImport(cu, "java.util.HashMap");
        ensureImport(cu, "java.util.List");
        ensureImport(cu, "java.util.ArrayList");
        return Optional.of("Вложенные индексные циклы с сопоставлением по ключу заменены индексом позиций HashMap, построенным один раз: "
                + "O(n²) → O(n). Добавлено: Map<Object, List<Integer>> " + mapName + ".");
    }

    /** Имя счётчика цикла for (int v = ...; ...) с одной переменной; null, если форма другая. */
    private static String loopVar(ForStmt f, boolean anyStart) {
        if (f.getInitialization().size() != 1 || !(f.getInitialization().get(0) instanceof com.github.javaparser.ast.expr.VariableDeclarationExpr vd)
                || vd.getVariables().size() != 1 || !vd.getElementType().isPrimitiveType()
                || !vd.getElementType().asString().equals("int")) return null;
        VariableDeclarator v = vd.getVariable(0);
        if (v.getInitializer().isEmpty()) return null;
        if (!anyStart && !v.getInitializer().get().toString().equals("0")) return null;
        if (f.getUpdate().size() != 1) return null;
        String u = f.getUpdate().get(0).toString();
        String name = v.getNameAsString();
        return u.equals(name + "++") || u.equals("++" + name) ? name : null;
    }

    /** Граница v &lt; N: возвращает текст N (X.size(), arr.length или имя), иначе null. */
    private static String loopBound(ForStmt f, String v) {
        if (v == null || f.getCompare().isEmpty()) return null;
        if (!(f.getCompare().get() instanceof com.github.javaparser.ast.expr.BinaryExpr be)
                || be.getOperator() != com.github.javaparser.ast.expr.BinaryExpr.Operator.LESS
                || !be.getLeft().toString().equals(v)) return null;
        return be.getRight().toString();
    }

    /** "next" для j = i + 1, "all" для j = 0, иначе null. */
    private static String innerStart(ForStmt inner, String i) {
        VariableDeclarator v = inner.getInitialization().get(0).asVariableDeclarationExpr().getVariable(0);
        String init = v.getInitializer().get().toString().replace(" ", "");
        if (init.equals(i + "+1")) return "next";
        if (init.equals("0")) return "all";
        return null;
    }

    /** Ключ строится из элемента X.get(idx) / X[idx] (возможно, с цепочкой полей и вызовов). */
    private static boolean usesElementAccess(Expression key, String coll, String idx) {
        for (MethodCallExpr mc : key.findAll(MethodCallExpr.class)) {
            if (mc.getNameAsString().equals("get") && mc.getArguments().size() == 1
                    && mc.getArgument(0).toString().equals(idx)
                    && mc.getScope().filter(sc -> sc.toString().equals(coll)).isPresent()) return true;
        }
        for (com.github.javaparser.ast.expr.ArrayAccessExpr aa : key.findAll(com.github.javaparser.ast.expr.ArrayAccessExpr.class)) {
            if (aa.getName().toString().equals(coll) && aa.getIndex().toString().equals(idx)) return true;
        }
        return false;
    }

    /** Для == : ключ — примитив (элемент int[] и т.п. либо поле примитивного типа класса из этого файла). */
    private static boolean isPrimitiveKey(CompilationUnit cu, CallableDeclaration<?> method, String coll, Expression key, String idx) {
        Type t = declaredType(method, coll);
        if (t == null) return false;
        if (key instanceof com.github.javaparser.ast.expr.ArrayAccessExpr) {
            return t.isArrayType() && t.asArrayType().getComponentType().isPrimitiveType()
                    && !t.asArrayType().getComponentType().asString().matches("double|float");
        }
        if (key instanceof com.github.javaparser.ast.expr.FieldAccessExpr fa && t.isClassOrInterfaceType()
                && t.asClassOrInterfaceType().getTypeArguments().filter(a -> a.size() == 1).isPresent()) {
            String elem = t.asClassOrInterfaceType().getTypeArguments().get().get(0).toString();
            for (com.github.javaparser.ast.body.ClassOrInterfaceDeclaration c
                    : cu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)) {
                if (!c.getNameAsString().equals(elem)) continue;
                for (com.github.javaparser.ast.body.FieldDeclaration fd : c.getFields()) {
                    for (VariableDeclarator v : fd.getVariables()) {
                        if (v.getNameAsString().equals(fa.getNameAsString()) && v.getType().isPrimitiveType()
                                && !v.getType().asString().matches("double|float")) return true;
                    }
                }
            }
        }
        return false;
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

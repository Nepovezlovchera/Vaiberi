package com.axenix.blueprints.analyzer;

import com.axenix.blueprints.model.Dto.AnalysisReport;
import com.axenix.blueprints.model.Dto.CodeIssue;
import com.axenix.blueprints.model.Dto.ComplexityMetric;
import com.axenix.blueprints.model.Dto.GraphNode;
import com.axenix.blueprints.model.Dto.MethodGraphResponse;
import com.axenix.blueprints.model.Dto.ProjectFile;
import com.axenix.blueprints.model.Services.AnalyzerService;
import com.axenix.blueprints.optimizer.TemplateFixes;
import com.axenix.blueprints.parser.Cyclomatic;
import com.axenix.blueprints.parser.GraphBuilder;
import com.axenix.blueprints.parser.ProjectModel;
import com.axenix.blueprints.parser.ProjectModel.MethodRef;
import com.axenix.blueprints.parser.StmtSlots;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.SimpleName;

import com.github.javaparser.ast.stmt.IfStmt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Собирает все найденные проблемы в единый отчёт и привязывает их к узлам графа. */
public class AnalyzerServiceImpl implements AnalyzerService {

    public static final int LONG_METHOD_LINES = 30;
    public static final int VERY_LONG_METHOD_LINES = 60;
    public static final int HIGH_CYCLOMATIC = 10;
    public static final int VERY_HIGH_CYCLOMATIC = 20;
    private static final int MIN_DUPLICATE_LINES = 4;
    private static final int MIN_DUP_CONDITION_LENGTH = 14;

    private final LoopComplexityAnalyzer loops = new LoopComplexityAnalyzer();

    @Override
    public AnalysisReport analyze(List<ProjectFile> files) {
        return analyze(ProjectModel.parse(files));
    }

    public AnalysisReport analyze(ProjectModel pm) {
        List<CodeIssue> issues = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        Map<String, MethodGraphResponse> graphs = new HashMap<>();
        Map<String, Integer> cyclomatic = new LinkedHashMap<>();
        int worstDegree = 0;

        for (MethodRef m : pm.methods) cyclomatic.put(m.id(), Cyclomatic.of(m.body()));

        // 1. Big-O: вложенные циклы и дорогие вызовы в цикле
        for (Map.Entry<String, CompilationUnit> e : pm.units.entrySet()) {
            for (LoopComplexityAnalyzer.Finding f : loops.analyze(e.getValue())) {
                MethodRef m = innermostMethod(pm, e.getKey(), f.startLine());
                if (m == null) continue;
                String nodeId = nodeFor(graphs, m, f.startLine(), f.endLine());
                boolean nested = f.type() == LoopComplexityAnalyzer.Type.NESTED_LOOP;
                worstDegree = Math.max(worstDegree, f.degree());
                String title = nested ? "Вложенность " + f.complexity() : "Дорогой вызов в цикле: " + f.complexity();
                String rec = nested
                        ? "Индексировать данные по ключу (HashMap/HashSet) вместо повторного прохода по коллекции."
                        : "Заменить List на HashSet/HashMap для поиска: проверка станет O(1) вместо O(n).";
                String after = LoopComplexityAnalyzer.complexityLabel(Math.max(1, f.degree() - 1));
                String suggested = suggestedCode(pm, m, f.startLine());
                issues.add(issue(usedIds, f.type().name(), f.degree() >= 3 ? "HIGH" : "MEDIUM", m, nodeId,
                        f.startLine(), f.endLine(), title, f.message() + " (метод " + m.id() + ")",
                        "Рост времени выполнения при росте входных данных: " + f.complexity() + ".", rec,
                        suggested, f.complexity(), after));
            }
        }

        // 2. Длинные методы и высокая цикломатическая сложность
        for (MethodRef m : pm.methods) {
            GraphBuilder.Pos r = GraphBuilder.pos(m.decl());
            int lines = r.lineEnd() - r.lineStart() + 1;
            String entry = StmtSlots.entryId(m.id());
            if (lines > LONG_METHOD_LINES) {
                issues.add(issue(usedIds, "LONG_METHOD", lines > VERY_LONG_METHOD_LINES ? "HIGH" : "MEDIUM", m, entry,
                        r.lineStart(), r.lineEnd(), "Длинный метод",
                        "Метод " + m.id() + " содержит " + lines + " строк (порог " + LONG_METHOD_LINES + ").",
                        "Сложно читать, тестировать и менять.", "Выделить часть логики в отдельные методы.",
                        null, null, null));
            }
            int cc = cyclomatic.get(m.id());
            if (cc > HIGH_CYCLOMATIC) {
                issues.add(issue(usedIds, "HIGH_CYCLOMATIC", cc > VERY_HIGH_CYCLOMATIC ? "HIGH" : "MEDIUM", m, entry,
                        r.lineStart(), r.lineEnd(), "Высокая цикломатическая сложность",
                        "Метод " + m.id() + ": сложность McCabe = " + cc + " (порог " + HIGH_CYCLOMATIC + ").",
                        "Много путей выполнения: растёт риск ошибок и стоимость тестов.",
                        "Разбить метод на части, упростить ветвления.", null, null, null));
            }
        }

        // 3. Дублирование (методы с одинаковым телом после нормализации имён переменных)
        Map<String, MethodRef> firstByHash = new HashMap<>();
        for (MethodRef m : pm.methods) {
            GraphBuilder.Pos r = GraphBuilder.pos(m.decl());
            if (m.body() == null || r.lineEnd() - r.lineStart() + 1 < MIN_DUPLICATE_LINES) continue;
            MethodRef first = firstByHash.putIfAbsent(normalizedBody(m), m);
            if (first != null) {
                issues.add(issue(usedIds, "DUPLICATION", "MEDIUM", m, StmtSlots.entryId(m.id()), r.lineStart(), r.lineEnd(),
                        "Дублирование логики",
                        "Метод " + m.id() + " повторяет " + first.id() + " (отличаются только имена переменных).",
                        "Исправление придётся вносить в нескольких местах.", "Вынести общий код в один метод.",
                        null, null, null));
            }
        }

        // 3b. Повторяющиеся условия (например, двойная проверка orders == null || orders.isEmpty())
        for (Map.Entry<String, CompilationUnit> e : pm.units.entrySet()) {
            Map<String, IfStmt> seen = new HashMap<>();
            for (IfStmt st : e.getValue().findAll(IfStmt.class)) {
                String cond = st.getCondition().toString().replaceAll("\\s+", "");
                if (cond.length() < MIN_DUP_CONDITION_LENGTH) continue;
                if (isSharedHelperCall(st.getCondition())) continue; // уже вынесено в общий метод
                IfStmt first = seen.putIfAbsent(cond, st);
                if (first == null) continue;
                int from = st.getCondition().getRange().map(r -> r.begin.line).orElse(1);
                int to = st.getCondition().getRange().map(r -> r.end.line).orElse(from);
                MethodRef m = innermostMethod(pm, e.getKey(), from);
                if (m == null) continue;
                int firstLine = first.getRange().map(r -> r.begin.line).orElse(0);
                issues.add(issue(usedIds, "DUPLICATION", "LOW", m, nodeFor(graphs, m, from, to), from, to,
                        "Повторяющееся условие",
                        "Условие «" + st.getCondition() + "» уже проверялось выше (строка " + firstLine + ").",
                        "Правка условия придётся вносить в нескольких местах; лишние проверки.",
                        "Вынести условие в метод или булеву переменную и использовать её повторно.",
                        null, null, null));
            }
        }

        int maxCc = cyclomatic.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        String current = worstDegree == 0 ? "≤ O(n)" : LoopComplexityAnalyzer.complexityLabel(worstDegree);
        String predicted = worstDegree == 0 ? "≤ O(n)" : LoopComplexityAnalyzer.complexityLabel(Math.max(1, worstDegree - 1));
        ComplexityMetric metrics = new ComplexityMetric(current, predicted, null, issues.size(), maxCc,
                pm.totalLines(), pm.methods.size(), cyclomatic);
        return new AnalysisReport(issues, metrics);
    }

    /** Худшая оценка Big-O по проекту в виде строки (для отчёта Quality Gate). */
    public String worstComplexity(ProjectModel pm) {
        return analyze(pm).metrics().currentComplexity();
    }

    private static CodeIssue issue(Set<String> used, String type, String severity, MethodRef m, String nodeId,
                                   int from, int to, String title, String description, String risk,
                                   String recommendation, String suggestedCode, String before, String after) {
        String id = type + ":" + nodeId;
        int k = 2;
        while (!used.add(id)) id = type + ":" + nodeId + "#" + k++;
        return new CodeIssue(id, nodeId, type, title, severity, from, to, m.file(), description, risk,
                recommendation, suggestedCode, before, after);
    }

    /** Исправленный метод целиком, если применим шаблонный автофикс (на копии AST, оригинал не меняется). */
    private static String suggestedCode(ProjectModel pm, MethodRef m, int line) {
        try {
            ProjectModel fresh = ProjectModel.parse(List.of(new ProjectFile(m.file(), pm.sources.get(m.file()))));
            if (TemplateFixes.apply(fresh.units.get(m.file()), line).isEmpty()) return null;
            return fresh.method(m.id()).decl().toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Самый вложенный метод файла, строки которого содержат line. */
    private static MethodRef innermostMethod(ProjectModel pm, String file, int line) {
        MethodRef best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (MethodRef m : pm.methods) {
            if (!m.file().equals(file)) continue;
            GraphBuilder.Pos r = GraphBuilder.pos(m.decl());
            if (r.lineStart() <= line && line <= r.lineEnd() && r.lineEnd() - r.lineStart() < bestSpan) {
                best = m;
                bestSpan = r.lineEnd() - r.lineStart();
            }
        }
        return best;
    }

    /** Наименьший узел графа метода, покрывающий строки from..to. */
    private static String nodeFor(Map<String, MethodGraphResponse> cache, MethodRef m, int from, int to) {
        MethodGraphResponse g = cache.computeIfAbsent(m.id(), k -> GraphBuilder.build(m));
        String best = StmtSlots.entryId(m.id());
        int bestSpan = Integer.MAX_VALUE;
        for (GraphNode n : g.nodes()) {
            if (n.type().equals("method")) continue;
            var d = n.data();
            if (d.lineStart() <= from && to <= d.lineEnd() && d.lineEnd() - d.lineStart() < bestSpan) {
                best = n.id();
                bestSpan = d.lineEnd() - d.lineStart();
            }
        }
        return best;
    }

    /** Условие вида helper(a, b) — вызов общего метода без объекта-получателя, повтором не считается. */
    private static boolean isSharedHelperCall(com.github.javaparser.ast.expr.Expression c) {
        return c instanceof com.github.javaparser.ast.expr.MethodCallExpr call && call.getScope().isEmpty()
                && call.getArguments().stream().allMatch(a -> a instanceof com.github.javaparser.ast.expr.NameExpr
                || a instanceof com.github.javaparser.ast.expr.LiteralExpr);
    }

    /** Тело метода с переименованными локальными переменными и параметрами (v1, v2, ...). */
    public static String normalizedBody(MethodRef m) {
        Node copy = m.decl().clone();
        Map<String, String> names = new LinkedHashMap<>();
        // параметры берём у оригинала: у компактного конструктора это компоненты record (вне клона)
        for (Parameter p : m.parameters()) names.putIfAbsent(p.getNameAsString(), "v" + (names.size() + 1));
        for (VariableDeclarator v : copy.findAll(VariableDeclarator.class)) names.putIfAbsent(v.getNameAsString(), "v" + (names.size() + 1));
        for (SimpleName sn : copy.findAll(SimpleName.class)) {
            String repl = names.get(sn.getIdentifier());
            if (repl != null) sn.setIdentifier(repl);
        }
        if (copy instanceof NodeWithSimpleName<?> named) named.setName("m");
        return copy.toString().replaceAll("\\s+", " ");
    }
}

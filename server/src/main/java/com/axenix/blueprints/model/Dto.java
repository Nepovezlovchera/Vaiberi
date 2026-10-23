package com.axenix.blueprints.model;

import java.util.List;
import java.util.Map;

/**
 * Контракт API. Совпадает с docs/ARCHITECTURE_AND_SPEC.md (разделы 3.1–3.3).
 * Менять только через PR с согласия фронта и бэка; фронтенд зеркалит типы в client/src/types.
 */
public final class Dto {
    private Dto() {}

    // ---------- общие ----------
    public record ProjectFile(String path, String source) {}
    public record ErrorResponse(String code, String message, Integer line) {}
    public record FilesRequest(List<ProjectFile> files) {}

    // ---------- граф (3.1) ----------
    /**
     * data узла. lineStart/lineEnd — строки с 1 (для Monaco), columnStart/columnEnd — колонки с 1.
     * issueId — первая проблема узла (для значка), issueIds — все.
     * loopType: for | foreach | while | do. condition — условие if/while/do/for или селектор switch.
     */
    public record NodeData(String label, String code, int lineStart, int lineEnd, int columnStart, int columnEnd,
                           boolean hasIssue, String issueId, List<String> issueIds,
                           String loopType, String condition, String header) {}

    /** type: method | var | condition | loop | op | call | return | try | switch | break | continue | throw | raw */
    public record GraphNode(String id, String parentId, String type, NodeData data) {}

    /** type: next | true | false | body | catch | finally; label: «Да», «Нет», «Тело цикла», ... */
    public record GraphEdge(String id, String source, String target, String type, String label) {}

    public record MethodGraphResponse(String className, String methodId, String methodSignature, String fileName,
                                      int lineStart, int lineEnd, List<GraphNode> nodes, List<GraphEdge> edges) {}

    /** methodId необязателен: если задан, вернётся только граф этого метода. */
    public record GraphRequest(List<ProjectFile> files, String methodId) {}
    public record GraphResponse(List<MethodGraphResponse> methods, ComplexityMetric metrics) {}

    // ---------- мутации графа (4.3) ----------
    /**
     * action: UPDATE_CODE | DELETE_NODE | INSERT_NODE | REORDER (перенос узла).
     * nodeId — узел; parentId — контейнер для INSERT_NODE / REORDER (методId/entry или методId/sN[:слот]);
     * index — позиция в контейнере; payload — код; template — шаблон блока из палитры (INSERT_NODE).
     * validate — проверять компиляцию результата (по умолчанию да; для вставки по template — нет).
     */
    public record MutateRequest(List<ProjectFile> files, String action, String nodeId, String parentId,
                                Integer index, String payload, String template, Boolean validate) {}
    public record MutateResponse(List<ProjectFile> files, GraphResponse graph, boolean validated, String compileOutput) {}

    // ---------- диагностика и Big-O (3.2) ----------
    /** type: NESTED_LOOP | COSTLY_CALL_IN_LOOP | LONG_METHOD | HIGH_CYCLOMATIC | DUPLICATION; severity: HIGH | MEDIUM | LOW */
    public record CodeIssue(String id, String nodeId, String type, String title, String severity,
                            int lineStart, int lineEnd, String fileName,
                            String description, String risk, String recommendation, String suggestedCode,
                            String complexityBefore, String complexityAfter) {}

    /** expectedSpeedup null, пока нет замера: реальное ускорение приходит из Quality Gate (VerificationReport.speedup). */
    public record ComplexityMetric(String currentComplexity, String predictedComplexity, String expectedSpeedup,
                                   int issuesCount, int cyclomaticComplexity, int linesOfCode,
                                   int methodCount, Map<String, Integer> cyclomaticByMethod) {}

    public record AnalysisReport(List<CodeIssue> issues, ComplexityMetric metrics) {}

    // ---------- оптимизация / декомпозиция ----------
    public record OptimizeRequest(List<ProjectFile> files, String issueId, boolean allowLlm) {}
    /** strategy: TEMPLATE | LLM | CACHED */
    public record OptimizeResponse(List<ProjectFile> files, String strategy, String explanation) {}

    /**
     * plan: класс → список имён статических методов (необязательно; без плана группы выбираются по смыслу:
     * Parser / Analyzer / Optimizer). extractClasses / splitMethods по умолчанию true.
     */
    public record DecomposeRequest(List<ProjectFile> files, Map<String, List<String>> plan,
                                   Boolean extractClasses, Boolean splitMethods) {}
    public record DecomposeResponse(List<ProjectFile> files, List<String> createdFiles, String explanation) {}

    // ---------- Quality Gate (3.3) ----------
    /** EXACT | SORTED_LINES (порядок HashMap) | MASKED (время/случайность замаскированы regex) */
    public enum CompareMode { EXACT, SORTED_LINES, MASKED }

    /** expectedStdout == null → эталон снимается с оригинала (golden). */
    public record TestCase(String name, String stdin, String expectedStdout) {}

    public record VerifyRequest(List<ProjectFile> original, List<ProjectFile> modified,
                                String mainClass, List<TestCase> tests, CompareMode mode) {}

    /** status: PASSED | FAILED | TIMEOUT | COMPILE_ERROR */
    public record TestCaseResult(String name, String status, long durationMs, String errorDetails) {}

    public record VerificationReport(boolean allPassed, boolean behaviorMatched,
                                     long originalTimeMs, long optimizedTimeMs, String speedup,
                                     String complexityBefore, String complexityAfter,
                                     List<TestCaseResult> testResults, List<String> logs,
                                     String compileOutput, int passed, int total, String timestamp) {}

    // ---------- запуск (консоль) ----------
    public record RunRequest(List<ProjectFile> files, String mainClass, String stdin) {}
    public record RunResponse(boolean compiled, String compileOutput, String stdout, String stderr,
                              int exitCode, boolean timedOut, long millis) {}
}

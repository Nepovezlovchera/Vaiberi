package com.axenix.blueprints.optimizer;

import com.axenix.blueprints.analyzer.AnalyzerServiceImpl;
import com.axenix.blueprints.model.Dto.AnalysisReport;
import com.axenix.blueprints.model.Dto.CodeIssue;
import com.axenix.blueprints.model.Dto.OptimizeRequest;
import com.axenix.blueprints.model.Dto.OptimizeResponse;
import com.axenix.blueprints.model.Dto.ProjectFile;
import com.axenix.blueprints.model.Dto.VerificationReport;
import com.axenix.blueprints.sandbox.CompilerService;
import com.axenix.blueprints.sandbox.ProcessRunner;
import com.axenix.blueprints.sandbox.VerifyServiceImpl;
import com.axenix.blueprints.support.Demo;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptimizerServiceTest {

    private final AnalyzerServiceImpl analyzer = new AnalyzerServiceImpl();
    private final OptimizerServiceImpl optimizer = new OptimizerServiceImpl(analyzer,
            new LlmGateway(Path.of("target/llm-cache-test"), null, null, null));
    private final VerifyServiceImpl verify = new VerifyServiceImpl(new CompilerService(), new ProcessRunner(5000, "128m"));

    private CodeIssue issue(List<ProjectFile> files, String type, String nodePrefix) {
        AnalysisReport r = analyzer.analyze(files);
        return r.issues().stream().filter(i -> i.type().equals(type) && i.nodeId().startsWith(nodePrefix)).findFirst().orElseThrow();
    }

    /** Главный демо-сценарий: автофикс O(n²) и доказательство сохранения поведения. */
    @Test
    void autofixOfUniqueCustomersPreservesBehavior() {
        List<ProjectFile> files = Demo.project();
        CodeIssue i = issue(files, "COSTLY_CALL_IN_LOOP", "Main.uniqueCustomers");
        OptimizeResponse r = optimizer.optimize(new OptimizeRequest(files, i.id(), false));
        assertEquals("TEMPLATE", r.strategy());
        String code = r.files().get(0).source();
        assertTrue(code.contains("HashSet"), code);
        assertTrue(code.contains("resultSet.add(o.customer)"), code);
        // проблема исчезла
        assertTrue(analyzer.analyze(r.files()).issues().stream()
                .noneMatch(x -> x.type().equals("COSTLY_CALL_IN_LOOP") && x.nodeId().startsWith("Main.uniqueCustomers")));
        VerificationReport v = verify.verify(Demo.verify(r.files()));
        assertTrue(v.behaviorMatched(), v.testResults().toString() + v.compileOutput());
    }

    @Test
    void readOnlyListInLoopUsesSetContains() {
        String src = "import java.util.*;\npublic class Main {\n"
                + "  static int count(List<String> a, List<String> b) {\n    int c = 0;\n"
                + "    for (String s : a) {\n      if (b.contains(s)) {\n        c++;\n      }\n    }\n    return c;\n  }\n"
                + "  public static void main(String[] x) { System.out.println(count(List.of(\"a\",\"b\"), List.of(\"b\"))); }\n}\n";
        List<ProjectFile> files = List.of(new ProjectFile("Main.java", src));
        CodeIssue i = issue(files, "COSTLY_CALL_IN_LOOP", "Main.count");
        OptimizeResponse r = optimizer.optimize(new OptimizeRequest(files, i.id(), false));
        String code = r.files().get(0).source();
        assertTrue(code.contains("Set<String> bSet = new HashSet<>(b);"), code);
        assertTrue(code.contains("bSet.contains(s)"), code);
    }

    @Test
    void unsafePatternIsNotAutofixed() {
        // условие с && — вставка set.add изменила бы семантику, поэтому фикс обязан отказаться
        String src = "import java.util.*;\npublic class Main {\n"
                + "  static List<String> f(List<String> a, boolean flag) {\n    List<String> r = new ArrayList<>();\n"
                + "    for (String s : a) {\n      if (!r.contains(s) && flag) {\n        r.add(s);\n      }\n    }\n    return r;\n  }\n}\n";
        List<ProjectFile> files = List.of(new ProjectFile("Main.java", src));
        CodeIssue i = issue(files, "COSTLY_CALL_IN_LOOP", "Main.f");
        assertThrows(IllegalArgumentException.class, () -> optimizer.optimize(new OptimizeRequest(files, i.id(), false)));
    }

    @Test
    void nestedLoopHasNoTemplateAndLlmIsOptIn() {
        List<ProjectFile> files = Demo.project();
        CodeIssue i = issue(files, "NESTED_LOOP", "Main.findDuplicates");
        assertThrows(IllegalArgumentException.class, () -> optimizer.optimize(new OptimizeRequest(files, i.id(), false)));
        // с allowLlm, но без ключа и кэша — понятная ошибка, а не падение
        assertThrows(IllegalArgumentException.class, () -> optimizer.optimize(new OptimizeRequest(files, i.id(), true)));
    }

    @Test
    void llmJsonHelpersRoundTrip() {
        String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"```java\\nclass A { String s = \\\"п\\u0440\\\"; }\\n```\"}}]}";
        String text = LlmGateway.extractContent(json);
        assertTrue(text.contains("class A { String s = \"пр\"; }"), text);
        assertEquals("\"a\\\"b\\n\"", LlmGateway.quote("a\"b\n"));
    }

    @Test
    void unknownIssueIsBadRequest() {
        assertThrows(IllegalArgumentException.class, () -> optimizer.optimize(new OptimizeRequest(Demo.project(), "NOPE", false)));
    }
}

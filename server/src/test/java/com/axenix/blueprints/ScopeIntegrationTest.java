package com.axenix.blueprints;

import com.axenix.blueprints.analyzer.AnalyzerServiceImpl;
import com.axenix.blueprints.model.Dto.*;
import com.axenix.blueprints.optimizer.*;
import com.axenix.blueprints.parser.*;
import com.axenix.blueprints.sandbox.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent programs exercise graph, analysis, transformations, javac and actual JVM execution. */
class ScopeIntegrationTest {
    private final AnalyzerServiceImpl analyzer = new AnalyzerServiceImpl();
    private final OptimizerServiceImpl optimizer = new OptimizerServiceImpl(analyzer,
            new LlmGateway(Path.of("target/scope-llm-cache"), null, null, null));
    private final DecomposeServiceImpl decompose = new DecomposeServiceImpl(new CompilerService());
    private final VerifyServiceImpl verify = new VerifyServiceImpl(new CompilerService(), new ProcessRunner(5000, "128m"));

    private List<ProjectFile> fixture(String name) throws Exception {
        return List.of(new ProjectFile("Main.java", Files.readString(Path.of("../samples", name, "Main.java"))));
    }

    private List<TestCase> inputs(String name) throws Exception {
        List<TestCase> cases = new ArrayList<>();
        try (var paths = Files.list(Path.of("../samples", name, "inputs"))) {
            for (Path input : paths.sorted().toList()) {
                cases.add(new TestCase(input.getFileName().toString(), Files.readString(input), null));
            }
        }
        assertEquals(8, cases.size());
        return cases;
    }

    @Test void mandatoryAndBonusScopeSurvivesFullPipeline() throws Exception {
        var original = fixture("scope");
        var pm = ProjectModel.parse(original);
        assertEquals(original, pm.render());
        var graphs = new ParseServiceImpl(analyzer).graph(original, null).methods();
        assertTrue(graphs.stream().anyMatch(g -> g.methodId().equals("Main.$static")));
        assertTrue(graphs.stream().anyMatch(g -> g.methodId().equals("Main.Metric.apply") && g.nodes().size() == 1));
        assertTrue(graphs.stream().flatMap(g -> g.nodes().stream()).anyMatch(n -> "do".equals(n.data().loopType())));
        assertTrue(graphs.stream().flatMap(g -> g.nodes().stream()).anyMatch(n -> n.data().code().contains("instanceof Metric")));
        assertTrue(graphs.stream().flatMap(g -> g.nodes().stream()).anyMatch(n -> n.data().code().contains("iterator.next")));
        var issues = analyzer.analyze(original).issues();
        assertTrue(issues.stream().anyMatch(i -> i.nodeId().startsWith("Main.allowedCount") && i.type().equals("COSTLY_CALL_IN_LOOP") && i.suggestedCode() != null));
        var optimized = optimizer.optimize(new OptimizeRequest(original, null, false)).files();
        assertTrue(optimized.get(0).source().contains("MembershipIndex"));
        var result = decompose.decompose(new DecomposeRequest(optimized, null, null, null));
        var report = verify.verify(new VerifyRequest(original, result.files(), null, inputs("scope"), CompareMode.EXACT));
        assertTrue(report.allPassed() && report.behaviorMatched(), report.compileOutput() + report.testResults());
    }

    @Test void thousandLineStressProjectSurvivesFullPipeline() throws Exception {
        var original = fixture("stress");
        assertTrue(original.get(0).source().lines().count() >= 900);
        assertTrue(new ParseServiceImpl(analyzer).graph(original, null).methods().size() > 40);
        assertFalse(analyzer.analyze(original).issues().isEmpty());
        var optimized = optimizer.optimize(new OptimizeRequest(original, null, false)).files();
        assertNotEquals(original, optimized);
        var result = decompose.decompose(new DecomposeRequest(optimized, null, null, null));
        var report = verify.verify(new VerifyRequest(original, result.files(), null, inputs("stress"), CompareMode.EXACT));
        assertTrue(report.allPassed() && report.behaviorMatched(), report.compileOutput() + report.testResults());
    }

    @Test void decompositionRollsBackUnresolvableClassFieldDependencies() {
        var files = List.of(new ProjectFile("Main.java", "class Main { private static int value = 3; static int calculate() { return value; } }"));
        var result = decompose.decompose(new DecomposeRequest(files, null, null, null));
        assertEquals(files, result.files());
        assertTrue(result.createdFiles().isEmpty());
        assertTrue(result.explanation().contains("отменена"));
    }

    @Test void unfamiliarLongMethodCanBeSplitInternally() {
        StringBuilder source = new StringBuilder("class Main {\n public static void main(String[] a) { execute(); }\n static void execute() {\n");
        for (int i = 0; i < 40; i++) source.append("System.out.println(\"step ").append(i).append("\");\n");
        source.append("}\n}\n");
        var files = List.of(new ProjectFile("Main.java", source.toString()));
        var result = decompose.decompose(new DecomposeRequest(files, null, false, true));
        assertNotEquals(files, result.files(), result.explanation());
        assertTrue(result.explanation().contains("вспомогательный метод"));
        var report = verify.verify(new VerifyRequest(files, result.files(), null, List.of(new TestCase("run", "", null)), CompareMode.EXACT));
        assertTrue(report.behaviorMatched(), report.compileOutput() + report.testResults());
    }

    @Test void streamFixRefusesLazyOrSideEffectingPipelines() {
        for (String expression : List.of("xs.stream().filter(x -> allowed.contains(x))",
                "xs.stream().filter(x -> allowed.contains(x)).peek(x -> allowed.clear()).toList()")) {
            String returnType = expression.endsWith("toList()") ? "List<String>" : "java.util.stream.Stream<String>";
            var files = List.of(new ProjectFile("A.java", "import java.util.*; class A { " + returnType
                    + " f(List<String> xs, List<String> allowed) { return " + expression + "; } }"));
            assertEquals(files, optimizer.optimize(new OptimizeRequest(files, null, false)).files());
        }
    }
}

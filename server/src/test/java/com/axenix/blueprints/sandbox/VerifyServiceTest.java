package com.axenix.blueprints.sandbox;

import com.axenix.blueprints.model.Dto.CompareMode;
import com.axenix.blueprints.model.Dto.ProjectFile;
import com.axenix.blueprints.model.Dto.RunRequest;
import com.axenix.blueprints.model.Dto.RunResponse;
import com.axenix.blueprints.model.Dto.TestCase;
import com.axenix.blueprints.model.Dto.VerifyRequest;
import com.axenix.blueprints.model.Dto.VerificationReport;
import com.axenix.blueprints.support.Demo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifyServiceTest {

    private final CompilerService compiler = new CompilerService();
    private final VerifyServiceImpl verify = new VerifyServiceImpl(compiler, new ProcessRunner(3000, "128m"));
    private final RunServiceImpl run = new RunServiceImpl(compiler, new ProcessRunner(3000, "128m"));

    private static List<ProjectFile> one(String src) {
        return List.of(new ProjectFile("Main.java", src));
    }

    @Test
    void runsDemoWithCyrillic() {
        RunResponse r = run.run(new RunRequest(Demo.project(), null, Demo.read("/demo/tests/basic.in")));
        assertTrue(r.compiled(), r.compileOutput());
        assertEquals(0, r.exitCode());
        assertTrue(r.stdout().contains("Клиенты: [Anna, Bob, Иван]"), r.stdout());
    }

    @Test
    void reportsCompileErrors() {
        RunResponse r = run.run(new RunRequest(one("public class Main { public static void main(String[] a) { int x = \"s\"; } }"), null, ""));
        assertFalse(r.compiled());
        assertTrue(r.compileOutput().contains("Main.java"));
    }

    @Test
    void identicalProjectPassesAllDemoTests() {
        VerificationReport r = verify.verify(Demo.verify(Demo.project()));
        assertTrue(r.behaviorMatched(), r.testResults().toString());
        assertEquals(4, r.total());
        assertEquals(4, r.passed());
    }

    @Test
    void changedOutputIsDetectedWithDiff() {
        String changed = Demo.read("/demo/Main.java").replace("Сумма: %.2f%n", "Итого: %.2f%n");
        VerificationReport r = verify.verify(Demo.verify(one(changed)));
        assertFalse(r.behaviorMatched());
        assertTrue(r.testResults().get(0).errorDetails().contains("строка 2"), r.testResults().get(0).errorDetails());
    }

    @Test
    void changedExitCodeIsDetected() {
        String orig = "public class Main { public static void main(String[] a) { System.out.println(1); } }";
        String mod = "public class Main { public static void main(String[] a) { System.out.println(1); System.exit(3); } }";
        VerificationReport r = verify.verify(new VerifyRequest(one(orig), one(mod), null, List.of(new TestCase("t", "", null)), CompareMode.EXACT));
        assertFalse(r.behaviorMatched());
        assertTrue(r.testResults().get(0).errorDetails().contains("код выхода"));
    }

    @Test
    void infiniteLoopIsKilledAsTimeout() {
        String orig = "public class Main { public static void main(String[] a) { System.out.println(1); } }";
        String mod = "public class Main { public static void main(String[] a) { while (true) { } } }";
        VerificationReport r = verify.verify(new VerifyRequest(one(orig), one(mod), null, List.of(new TestCase("t", "", null)), CompareMode.EXACT));
        assertEquals("TIMEOUT", r.testResults().get(0).status());
    }

    @Test
    void brokenModifiedVersionFailsBuild() {
        String orig = "public class Main { public static void main(String[] a) { } }";
        String mod = "public class Main { public static void main(String[] a) { oops(); } }";
        VerificationReport r = verify.verify(new VerifyRequest(one(orig), one(mod), null, null, null));
        assertFalse(r.behaviorMatched());
        assertTrue(r.compileOutput().contains("не компилируется") && r.testResults().get(0).status().equals("COMPILE_ERROR"));
    }

    @Test
    void sortedLinesModeToleratesOrderChanges() {
        String orig = "public class Main { public static void main(String[] a) { System.out.println(\"a\"); System.out.println(\"b\"); } }";
        String mod = "public class Main { public static void main(String[] a) { System.out.println(\"b\"); System.out.println(\"a\"); } }";
        List<TestCase> t = List.of(new TestCase("t", "", null));
        assertFalse(verify.verify(new VerifyRequest(one(orig), one(mod), null, t, CompareMode.EXACT)).behaviorMatched());
        assertTrue(verify.verify(new VerifyRequest(one(orig), one(mod), null, t, CompareMode.SORTED_LINES)).behaviorMatched());
    }

    @Test
    void maskedModeIgnoresTimings() {
        String orig = "public class Main { public static void main(String[] a) { System.out.println(\"Время: 1.24 сек\"); } }";
        String mod = "public class Main { public static void main(String[] a) { System.out.println(\"Время: 0.31 сек\"); } }";
        List<TestCase> t = List.of(new TestCase("t", "", null));
        assertFalse(verify.verify(new VerifyRequest(one(orig), one(mod), null, t, CompareMode.EXACT)).behaviorMatched());
        assertTrue(verify.verify(new VerifyRequest(one(orig), one(mod), null, t, CompareMode.MASKED)).behaviorMatched());
    }

    @Test
    void uncaughtExceptionTypeIsCompared() {
        String orig = "public class Main { public static void main(String[] a) { throw new IllegalStateException(\"x\"); } }";
        String same = "public class Main {\n\n public static void main(String[] a) {\n throw new IllegalStateException(\"x\"); } }";
        String other = "public class Main { public static void main(String[] a) { throw new IllegalArgumentException(\"x\"); } }";
        List<TestCase> t = List.of(new TestCase("t", "", null));
        assertTrue(verify.verify(new VerifyRequest(one(orig), one(same), null, t, CompareMode.EXACT)).behaviorMatched());
        assertFalse(verify.verify(new VerifyRequest(one(orig), one(other), null, t, CompareMode.EXACT)).behaviorMatched());
    }

    @Test
    void reportHasTimingsLogsAndIsSavedAsJson() throws Exception {
        java.nio.file.Path f = java.nio.file.Files.createTempFile("vr", ".json");
        VerifyServiceImpl v = new VerifyServiceImpl(compiler, new ProcessRunner(3000, "128m"),
                new com.axenix.blueprints.analyzer.AnalyzerServiceImpl(), new ReportStore(f));
        java.util.List<String> lines = new java.util.ArrayList<>();
        VerificationReport r = v.verify(Demo.verify(Demo.project()), lines::add);
        assertTrue(r.allPassed());
        assertTrue(r.originalTimeMs() > 0 && r.optimizedTimeMs() > 0);
        assertTrue(r.speedup() != null && !r.logs().isEmpty());
        assertEquals(r.logs(), lines);
        String json = java.nio.file.Files.readString(f);
        assertTrue(json.contains("\"behaviorMatched\": true") || json.contains("\"behaviorMatched\":true"), json);
        assertTrue(json.contains("testResults"));
        java.nio.file.Files.deleteIfExists(f);
    }
}

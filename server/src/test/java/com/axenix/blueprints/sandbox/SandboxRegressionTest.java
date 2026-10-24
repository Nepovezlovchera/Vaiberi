package com.axenix.blueprints.sandbox;

import com.axenix.blueprints.model.Dto.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SandboxRegressionTest {
    private final CompilerService compiler = new CompilerService();
    private final RunServiceImpl run = new RunServiceImpl(compiler, new ProcessRunner(3000, "128m"));
    private final VerifyServiceImpl verify = new VerifyServiceImpl(compiler, new ProcessRunner(3000, "128m"));
    private static List<ProjectFile> program(String body) {
        return List.of(new ProjectFile("Main.java", "public class Main { public static void main(String[] args) throws Exception { " + body + " } }"));
    }

    @Test void expectedOutputCannotReplaceOriginalComparison() {
        var report = verify.verify(new VerifyRequest(program("System.out.print(\"original\");"),
                program("System.out.print(\"modified\");"), null, List.of(new TestCase("t", "", "modified")), CompareMode.EXACT));
        assertFalse(report.behaviorMatched());
    }

    @Test void exactPreservesTrailingWhitespaceAndNewlines() {
        assertNotEquals(OutputComparator.normalize("x ", CompareMode.EXACT), OutputComparator.normalize("x", CompareMode.EXACT));
        assertNotEquals(OutputComparator.normalize("x\n", CompareMode.EXACT), OutputComparator.normalize("x", CompareMode.EXACT));
        assertEquals("at user data\n", OutputComparator.stripStackTrace("at user data\n"));
        assertEquals("failure\n", OutputComparator.stripStackTrace("failure\n\tat Main.main(Main.java:1)\n"));
    }

    @Test void truncatedOutputNeverPasses() {
        var source = program("System.out.print(\"x\".repeat(1000001));");
        var report = verify.verify(new VerifyRequest(source, source, null, List.of(new TestCase("t", "", null)), CompareMode.EXACT));
        assertFalse(report.allPassed());
        assertTrue(report.testResults().get(0).errorDetails().contains("1MB"));
    }

    @Test void detectsActualMainClassWithDifferentModifierOrderAndVarargs() {
        assertEquals("demo.Runner", MainClassFinder.find(List.of(new ProjectFile("Main.java",
                "package demo; class Other {} class Runner { static public void main(String... args) {} }")), null));
    }

    @Test void deniesReadingApiFilesAndEnvironmentAndStartingProcesses() {
        String body = "int denied=0; "
                + "try { java.nio.file.Files.readString(java.nio.file.Path.of(\"/etc/os-release\")); } catch (SecurityException e) { denied++; } "
                + "try { System.getenv(\"LLM_API_KEY\"); } catch (SecurityException e) { denied++; } "
                + "try { new ProcessBuilder(\"echo\", \"x\").start(); } catch (SecurityException e) { denied++; } "
                + "try { new java.net.Socket(\"127.0.0.1\", 8080); } catch (SecurityException e) { denied++; } "
                + "System.out.print(denied);";
        var result = run.run(new RunRequest(program(body), null, ""));
        assertTrue(result.compiled(), result.compileOutput());
        assertEquals(0, result.exitCode(), result.stderr());
        assertEquals("4", result.stdout());
    }
}

package com.axenix.blueprints.optimizer;

import com.axenix.blueprints.model.Dto.DecomposeRequest;
import com.axenix.blueprints.model.Dto.DecomposeResponse;
import com.axenix.blueprints.model.Dto.ProjectFile;
import com.axenix.blueprints.model.Dto.VerificationReport;
import com.axenix.blueprints.sandbox.CompilerService;
import com.axenix.blueprints.sandbox.ProcessRunner;
import com.axenix.blueprints.sandbox.VerifyServiceImpl;
import com.axenix.blueprints.support.Demo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecomposeServiceTest {

    private final CompilerService compiler = new CompilerService();
    private final DecomposeServiceImpl service = new DecomposeServiceImpl(compiler);
    private final VerifyServiceImpl verify = new VerifyServiceImpl(compiler, new ProcessRunner(5000, "128m"));

    @Test
    void extractsClassesSplitsMethodsAndKeepsBehavior() {
        DecomposeResponse r = service.decompose(new DecomposeRequest(Demo.project(), null, null, null));
        List<String> created = r.createdFiles();
        assertTrue(created.containsAll(List.of("Order.java", "InvalidOrderException.java", "Stats.java")), created.toString());
        assertTrue(created.stream().anyMatch(n -> n.equals("Parser.java") || n.equals("Analyzer.java") || n.equals("Optimizer.java")),
                "методы разнесены по Parser/Analyzer/Optimizer: " + created);
        String main = r.files().get(0).source();
        assertFalse(main.contains("class Order"), main);
        String order = r.files().stream().filter(f -> f.path().equals("Order.java")).findFirst().orElseThrow().source();
        assertTrue(order.contains("class Order") && !order.contains("static class"), order);

        VerificationReport v = verify.verify(Demo.verify(r.files()));
        assertTrue(v.behaviorMatched(), v.testResults().toString() + v.compileOutput() + r.files().stream().map(ProjectFile::path).toList());
    }

    @Test
    void onlyNestedClassesWhenSplitDisabled() {
        DecomposeResponse r = service.decompose(new DecomposeRequest(Demo.project(), null, true, false));
        assertEquals(List.of("Order.java", "InvalidOrderException.java", "Stats.java"), r.createdFiles());
        VerificationReport v = verify.verify(Demo.verify(r.files()));
        assertTrue(v.behaviorMatched(), v.testResults().toString() + v.compileOutput());
    }

    @Test
    void nothingToExtractLeavesProjectUntouched() {
        List<ProjectFile> files = List.of(new ProjectFile("A.java", "class A { void m() {} }"));
        DecomposeResponse r = service.decompose(new DecomposeRequest(files, null, null, null));
        assertTrue(r.createdFiles().isEmpty());
        assertEquals(files, r.files());
    }

    @Test
    void doesNotOverwriteExistingFiles() {
        List<ProjectFile> files = List.of(
                new ProjectFile("A.java", "class A { static class B {} }"),
                new ProjectFile("B.java", "class B {}"));
        DecomposeResponse r = service.decompose(new DecomposeRequest(files, null, null, null));
        assertTrue(r.createdFiles().isEmpty());
        assertTrue(r.explanation().contains("уже существует"));
    }
}

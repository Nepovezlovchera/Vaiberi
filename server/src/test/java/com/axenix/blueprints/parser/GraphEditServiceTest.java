package com.axenix.blueprints.parser;

import com.axenix.blueprints.analyzer.AnalyzerServiceImpl;
import com.axenix.blueprints.model.Dto.MutateRequest;
import com.axenix.blueprints.model.Dto.MutateResponse;
import com.axenix.blueprints.model.Dto.ProjectFile;
import com.axenix.blueprints.parser.ParseServiceImpl;
import com.axenix.blueprints.sandbox.CompilerService;
import com.axenix.blueprints.sandbox.RunServiceImpl;
import com.axenix.blueprints.support.Demo;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphEditServiceTest {

    private final GraphEditServiceImpl service = new GraphEditServiceImpl(new ParseServiceImpl(new AnalyzerServiceImpl()), new CompilerService());

    private static final String SRC = String.join("\n",
            "public class Main {",
            "    static int f(int n) {",
            "        int s = 0;",
            "        for (int i = 0; i < n; i++) {",
            "            s += i;",
            "        }",
            "        return s;",
            "    }",
            "}", "");

    private MutateResponse apply(String src, MutateRequest op) {
        return service.mutate(new MutateRequest(List.of(new ProjectFile("Main.java", src)), op.action(), op.nodeId(),
                op.parentId(), op.index(), op.payload(), op.template(), op.validate()));
    }

    private static MutateRequest op(String action, String nodeId, String parentId, Integer index, String code, String template) {
        String a = switch (action) {
            case "updateCode" -> "UPDATE_CODE";
            case "delete" -> "DELETE_NODE";
            case "insert" -> "INSERT_NODE";
            case "move" -> "REORDER";
            default -> action;
        };
        return new MutateRequest(null, a, nodeId, parentId, index, code, template, null);
    }

    private static String code(MutateResponse r) {
        return r.files().get(0).source();
    }

    private static boolean compiles(String src) throws Exception {
        Path dir = Files.createTempDirectory("bp-t-");
        try {
            return new CompilerService().compile(List.of(new ProjectFile("Main.java", src)), dir).ok();
        } finally {
            RunServiceImpl.deleteQuietly(dir);
        }
    }

    @Test
    void updateCodeReplacesOnlyTheNodeAndKeepsFormatting() throws Exception {
        MutateResponse r = apply(SRC, op("updateCode", "Main.f/s2.b.1", null, null, "s += i * 2;", null));
        assertEquals(SRC.replace("s += i;", "s += i * 2;"), code(r));
        assertTrue(compiles(code(r)));
    }

    @Test
    void deleteRemovesStatementAndRebuildsGraph() {
        MutateResponse r = apply(SRC, new MutateRequest(null, "DELETE_NODE", "Main.f/s3", null, null, null, null, false));
        assertFalse(code(r).contains("return s;"));
        assertEquals(1, r.graph().methods().stream().filter(m -> m.methodId().equals("Main.f")).count());
    }

    @Test
    void insertIntoLoopBodyAtIndex() throws Exception {
        MutateResponse r = apply(SRC, op("insert", null, "Main.f/s2", 0, "s++;", null));
        String c = code(r);
        assertTrue(c.indexOf("s++;") < c.indexOf("s += i;"), c);
        assertTrue(compiles(c));
    }

    @Test
    void insertTemplateIntoMethodBody() {
        MutateResponse r = apply(SRC, op("insert", null, "Main.f/entry", 0, null, "var"));
        assertTrue(code(r).contains("int value = 0;"));
    }

    @Test
    void moveStatementIntoLoop() throws Exception {
        String src = SRC.replace("        return s;", "        s++;\n        return s;");
        // переносим `s++;` (узел s3) в тело цикла на позицию 1 (индексы вставки 0-based)
        MutateResponse r = apply(src, op("move", "Main.f/s3", "Main.f/s2", 1, null, null));
        String c = code(r);
        assertTrue(c.indexOf("s += i;") < c.indexOf("s++;") && c.indexOf("s++;") < c.indexOf("return s;"), c);
        assertTrue(compiles(c));
        assertEquals(1, c.split("s\\+\\+;", -1).length - 1);
    }

    @Test
    void moveIntoItselfIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> apply(SRC, op("move", "Main.f/s2", "Main.f/s2.b.1", 0, null, null)));
    }

    @Test
    void unbracedBodyIsWrappedOnEdit() throws Exception {
        String src = "public class Main { static void g(int n) { if (n > 0) System.out.println(n); } }";
        MutateResponse r = apply(src, op("insert", null, "Main.g/s1:t", 1, "System.out.println(\"after\");", null));
        assertTrue(compiles(code(r)));
        assertTrue(code(r).contains("after"));
    }

    @Test
    void errorsAreReportedAsBadRequests() {
        assertThrows(IllegalArgumentException.class, () -> apply(SRC, op("delete", "Main.f/s9", null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> apply(SRC, op("delete", "Main.nope/s1", null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> apply(SRC, op("updateCode", "Main.f/s3", null, null, "int = ;", null)));
        assertThrows(IllegalArgumentException.class, () -> apply(SRC, op("explode", "Main.f/s3", null, null, null, null)));
    }

    @Test
    void untouchedFilesStayByteIdentical() {
        List<ProjectFile> files = List.of(new ProjectFile("Main.java", SRC),
                new ProjectFile("Other.java", "class   Other {  }\n"));
        MutateResponse r = service.mutate(new MutateRequest(files, "DELETE_NODE", "Main.f/s3", null, null, null, null, false));
        assertEquals("class   Other {  }\n", r.files().get(1).source());
    }

    @Test
    void demoRoundTripAfterNoopUpdateStillCompiles() throws Exception {
        String src = Demo.project().get(0).source();
        MutateResponse r = apply(src, op("updateCode", "Main.total/s2.b.1", null, null, "sum += o.amount;", null));
        assertEquals(src, code(r));
        assertTrue(compiles(code(r)));
    }

    @Test
    void mutationBreakingCompilationIsRejected() {
        // `int x = "str";` разбирается, но не компилируется → 400 (CompileErrorException)
        assertThrows(CompileErrorException.class,
                () -> apply(SRC, op("updateCode", "Main.f/s1", null, null, "int x = \"str\";", null)));
    }

    @Test
    void validationCanBeDisabled() {
        MutateResponse r = apply(SRC, new MutateRequest(null, "UPDATE_CODE", "Main.f/s1", null, null,
                "int x = \"str\";", null, false));
        assertFalse(r.validated());
    }

    @Test
    void validMutationIsMarkedValidated() {
        assertTrue(apply(SRC, op("updateCode", "Main.f/s3", null, null, "return s * 2;", null)).validated());
    }

    @Test
    void deletingReturnBreaksCompilationAndIsRejected() {
        assertThrows(CompileErrorException.class, () -> apply(SRC, op("delete", "Main.f/s3", null, null, null, null)));
    }
}

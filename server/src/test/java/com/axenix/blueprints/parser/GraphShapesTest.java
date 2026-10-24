package com.axenix.blueprints.parser;

import com.axenix.blueprints.analyzer.AnalyzerServiceImpl;
import com.axenix.blueprints.model.Dto.GraphEdge;
import com.axenix.blueprints.model.Dto.GraphNode;
import com.axenix.blueprints.model.Dto.GraphResponse;
import com.axenix.blueprints.model.Dto.MethodGraphResponse;
import com.axenix.blueprints.model.Dto.MutateRequest;
import com.axenix.blueprints.model.Dto.MutateResponse;
import com.axenix.blueprints.model.Dto.ProjectFile;
import com.axenix.blueprints.sandbox.CompilerService;
import com.axenix.blueprints.sandbox.RunServiceImpl;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Форма графа для меток, switch со стрелками и подписи рёбер switch; правки по тем же ID. */
class GraphShapesTest {

    private final ParseServiceImpl parse = new ParseServiceImpl(new AnalyzerServiceImpl());
    private final GraphEditServiceImpl edit = new GraphEditServiceImpl(parse, new CompilerService());

    private static final String SRC = String.join("\n",
            "public class Main {",
            "    static int pairs(int[] a, int n) {",
            "        int c = 0;",
            "        outer: for (int i = 0; i < n; i++) {",
            "            for (int j = 0; j < n; j++) {",
            "                if (a[i] == a[j]) continue outer;",
            "                if (a[i] < 0) break outer;",
            "                c++;",
            "            }",
            "        }",
            "        return c;",
            "    }",
            "    static String sw(String k) {",
            "        switch (k) {",
            "            case \"X\" -> {",
            "                System.out.println(\"one\");",
            "                return \"a\";",
            "            }",
            "            case \"A\", \"B\" -> System.out.println(\"two\");",
            "            default -> throw new IllegalStateException();",
            "        }",
            "        return \"z\";",
            "    }",
            "    static int old(int k) {",
            "        int r = 0;",
            "        switch (k) {",
            "            case 1:",
            "            case 2:",
            "                r = 1;",
            "                break;",
            "            default:",
            "                r = 2;",
            "        }",
            "        return r;",
            "    }",
            "    public static void main(String[] args) {",
            "        System.out.println(pairs(new int[]{1, 2}, 2) + sw(\"A\") + old(1));",
            "    }",
            "}", "");

    private MethodGraphResponse method(String src, String id) {
        GraphResponse r = parse.graph(List.of(new ProjectFile("Main.java", src)), null);
        return r.methods().stream().filter(m -> m.methodId().equals(id)).findFirst().orElseThrow();
    }

    private static GraphNode node(MethodGraphResponse g, String id) {
        return g.nodes().stream().filter(n -> n.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("нет узла " + id + " в " + g.nodes().stream().map(GraphNode::id).toList()));
    }

    private static GraphEdge edge(MethodGraphResponse g, String from, String to) {
        return g.edges().stream().filter(e -> e.source().equals(from) && e.target().equals(to)).findFirst()
                .orElseThrow(() -> new AssertionError("нет ребра " + from + " → " + to + " в " + g.edges()));
    }

    private MutateResponse apply(String action, String nodeId, String parentId, Integer index, String payload) {
        return edit.mutate(new MutateRequest(List.of(new ProjectFile("Main.java", SRC)), action, nodeId, parentId,
                index, payload, null, null));
    }

    private static boolean compiles(String src) throws Exception {
        Path dir = Files.createTempDirectory("bp-t-");
        try {
            return new CompilerService().compile(List.of(new ProjectFile("Main.java", src)), dir).ok();
        } finally {
            RunServiceImpl.deleteQuietly(dir);
        }
    }

    // ---------- метки ----------

    @Test
    void labelledLoopIsTransparentLoopNode() {
        MethodGraphResponse g = method(SRC, "Main.pairs");
        GraphNode outer = node(g, "Main.pairs/s2");
        assertEquals("loop", outer.type());
        assertEquals("for", outer.data().loopType());
        assertEquals("i < n", outer.data().condition());
        assertEquals("outer: for (int i = 0; i < n; i++)", outer.data().label());
        assertEquals("outer: for (int i = 0; i < n; i++)", outer.data().header());

        assertEquals("loop", node(g, "Main.pairs/s2.b.1").type());
        assertEquals("condition", node(g, "Main.pairs/s2.b.1.b.1").type());
        assertEquals("continue", node(g, "Main.pairs/s2.b.1.b.1.t.1").type());
        assertEquals("break", node(g, "Main.pairs/s2.b.1.b.2.t.1").type());
        assertEquals("op", node(g, "Main.pairs/s2.b.1.b.3").type());

        GraphEdge body = edge(g, "Main.pairs/s2", "Main.pairs/s2.b.1");
        assertEquals("body", body.type());
        assertEquals("Тело цикла", body.label());
        assertEquals("back", edge(g, "Main.pairs/s2.b.1", "Main.pairs/s2").type());
    }

    @Test
    void nestedLoopIssueLandsOnLabelledLoopNode() {
        MethodGraphResponse g = method(SRC, "Main.pairs");
        GraphNode flagged = g.nodes().stream()
                .filter(n -> n.data().hasIssue() && n.data().issueIds().stream().anyMatch(i -> i.startsWith("NESTED_LOOP")))
                .findFirst().orElseThrow(() -> new AssertionError(g.nodes().toString()));
        assertEquals("Main.pairs/s2", flagged.id());
        assertEquals("loop", flagged.type());
    }

    // ---------- switch ----------

    @Test
    void arrowCaseBlockStatementsBecomeNodes() {
        MethodGraphResponse g = method(SRC, "Main.sw");
        assertEquals("switch", node(g, "Main.sw/s1").type());
        assertEquals("call", node(g, "Main.sw/s1.s0.1").type());
        GraphNode ret = node(g, "Main.sw/s1.s0.2");
        assertEquals("return", ret.type());
        assertEquals("return \"a\";", ret.data().label());
        assertEquals("call", node(g, "Main.sw/s1.s1.1").type());
        assertEquals("throw", node(g, "Main.sw/s1.s2.1").type());
        assertFalse(g.nodes().stream().anyMatch(n -> n.type().equals("raw")), g.nodes().toString());
    }

    @Test
    void switchEdgesAreLabelledWithCases() {
        MethodGraphResponse g = method(SRC, "Main.sw");
        GraphEdge e0 = edge(g, "Main.sw/s1", "Main.sw/s1.s0.1");
        assertEquals("body", e0.type());
        assertEquals("case \"X\"", e0.label());
        assertEquals("case \"A\", \"B\"", edge(g, "Main.sw/s1", "Main.sw/s1.s1.1").label());
        assertEquals("default", edge(g, "Main.sw/s1", "Main.sw/s1.s2.1").label());
    }

    @Test
    void fallthroughCaseKeepsEntryIndexes() {
        MethodGraphResponse g = method(SRC, "Main.old");
        // Both labels enter the same body; empty entries keep the subsequent slot indexes.
        assertEquals("op", node(g, "Main.old/s2.s1.1").type());
        assertEquals("break", node(g, "Main.old/s2.s1.2").type());
        assertEquals("op", node(g, "Main.old/s2.s2.1").type());
        assertTrue(g.edges().stream().anyMatch(e -> e.source().equals("Main.old/s2")
                && e.target().equals("Main.old/s2.s1.1") && "case 1".equals(e.label())));
        assertTrue(g.edges().stream().anyMatch(e -> e.source().equals("Main.old/s2")
                && e.target().equals("Main.old/s2.s1.1") && "case 2".equals(e.label())));
        assertEquals("Main.pairs/s2", edge(method(SRC, "Main.pairs"), "Main.pairs/s2.b.1.b.1.t.1", "Main.pairs/s2").target());
        assertEquals("Main.pairs/s3", edge(method(SRC, "Main.pairs"), "Main.pairs/s2.b.1.b.2.t.1", "Main.pairs/s3").target());
        assertEquals("default", edge(g, "Main.old/s2", "Main.old/s2.s2.1").label());
        assertFalse(g.nodes().stream().anyMatch(n -> n.id().startsWith("Main.old/s2.s0.")));
    }

    @Test
    void fallthroughEntryIdsMatchEditLocate() throws Exception {
        MutateResponse r = apply("UPDATE_CODE", "Main.old/s2.s2.1", null, null, "r = 3;");
        String c = r.files().get(0).source();
        assertTrue(c.contains("r = 3;") && c.contains("r = 1;") && !c.contains("r = 2;"), c);
        assertTrue(compiles(c));
    }

    // ---------- правки ----------

    @Test
    void updateCodeInsideLabelledLoopBody() throws Exception {
        MutateResponse r = apply("UPDATE_CODE", "Main.pairs/s2.b.1.b.3", null, null, "c += 2;");
        String c = r.files().get(0).source();
        assertEquals(SRC.replace("c++;", "c += 2;"), c);
        assertTrue(r.validated());
        assertTrue(compiles(c));
    }

    @Test
    void insertIntoArrowCaseBlock() throws Exception {
        MutateResponse r = apply("INSERT_NODE", null, "Main.sw/s1:s0", 0, "System.out.println(\"first\");");
        String c = r.files().get(0).source();
        assertTrue(c.indexOf("\"first\"") >= 0 && c.indexOf("\"first\"") < c.indexOf("\"one\""), c);
        assertTrue(r.validated());
        assertTrue(compiles(c));
        MethodGraphResponse g = r.graph().methods().stream().filter(m -> m.methodId().equals("Main.sw")).findFirst().orElseThrow();
        assertTrue(node(g, "Main.sw/s1.s0.1").data().label().contains("first"));
        assertEquals("return", node(g, "Main.sw/s1.s0.3").type());
    }

    @Test
    void insertIntoExpressionArrowCaseWrapsItInBlock() throws Exception {
        MutateResponse r = apply("INSERT_NODE", null, "Main.sw/s1:s1", 1, "System.out.println(\"more\");");
        String c = r.files().get(0).source();
        assertTrue(c.indexOf("\"two\"") < c.indexOf("\"more\""), c);
        assertTrue(r.validated());
        assertTrue(compiles(c));
        MethodGraphResponse g = r.graph().methods().stream().filter(m -> m.methodId().equals("Main.sw")).findFirst().orElseThrow();
        assertTrue(node(g, "Main.sw/s1.s1.2").data().label().contains("more"));
    }

    @Test
    void deleteLabelledStatementRemovesLabelAndLoop() throws Exception {
        MutateResponse r = apply("DELETE_NODE", "Main.pairs/s2", null, null, null);
        String c = r.files().get(0).source();
        assertFalse(c.contains("outer"), c);
        assertFalse(c.contains("for (int i"), c);
        assertTrue(c.contains("return c;"), c);
        assertTrue(compiles(c));
    }
}

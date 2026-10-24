package com.axenix.blueprints.parser;

import com.axenix.blueprints.analyzer.AnalyzerServiceImpl;
import com.axenix.blueprints.model.Dto.GraphEdge;
import com.axenix.blueprints.model.Dto.GraphNode;
import com.axenix.blueprints.model.Dto.GraphResponse;
import com.axenix.blueprints.model.Dto.MethodGraphResponse;
import com.axenix.blueprints.model.Dto.ProjectFile;
import com.axenix.blueprints.parser.JavaParseException;
import com.axenix.blueprints.parser.ProjectModel;
import com.axenix.blueprints.support.Demo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParseServiceTest {

    private final ParseServiceImpl service = new ParseServiceImpl(new AnalyzerServiceImpl());

    private MethodGraphResponse method(GraphResponse r, String id) {
        return r.methods().stream().filter(m -> m.methodId().equals(id)).findFirst().orElseThrow();
    }

    /** Круговой тест: parse → печать без правок даёт исходник байт-в-байт. */
    @Test
    void roundTripWithoutEditsIsByteIdentical() {
        List<ProjectFile> files = Demo.project();
        ProjectModel pm = ProjectModel.parse(files);
        assertEquals(files.get(0).source(), pm.render().get(0).source());
        // и через LexicalPreservingPrinter напрямую (даже если файл помечен изменённым)
        pm.touch("Main.java");
        assertEquals(files.get(0).source(), pm.render().get(0).source());
    }

    @Test
    void buildsGraphForEveryMethod() {
        GraphResponse r = service.graph(Demo.project(), null);
        assertTrue(r.methods().size() >= 9);
        assertNotNull(method(r, "Main.uniqueCustomers"));
        assertNotNull(method(r, "Main.Stats.of"));
        assertNotNull(method(r, "Main.main"));
        assertEquals(r.methods().size(), r.metrics().methodCount());
    }

    @Test
    void loopContainsIfAndEdgesAreTyped() {
        MethodGraphResponse g = method(service.graph(Demo.project(), null), "Main.uniqueCustomers");
        GraphNode loop = g.nodes().stream().filter(n -> n.type().equals("loop")).findFirst().orElseThrow();
        assertEquals("loop", loop.type());
        GraphNode iff = g.nodes().stream().filter(n -> n.id().startsWith(loop.id() + ".b.") && n.type().equals("condition")).findFirst().orElseThrow();
        assertEquals("condition", iff.type());
        assertEquals(loop.id(), iff.parentId());
        assertTrue(iff.data().hasIssue() && iff.data().issueId().startsWith("COSTLY_CALL_IN_LOOP"), "проблема привязана к самому мелкому узлу — условию");
        boolean bodyEdge = g.edges().stream().anyMatch(e -> e.source().equals(loop.id()) && e.target().equals(iff.id()) && e.type().equals("body"));
        assertTrue(bodyEdge);
        GraphEdge first = g.edges().get(0);
        assertEquals("Main.uniqueCustomers/entry", first.source());
        assertEquals("next", first.type());
    }

    @Test
    void issuesAreAttachedToNodes() {
        GraphResponse r = service.graph(Demo.project(), null);
        boolean flagged = method(r, "Main.uniqueCustomers").nodes().stream().anyMatch(n -> n.data().hasIssue() && n.data().issueId().startsWith("COSTLY_CALL_IN_LOOP"));
        boolean nested = method(r, "Main.findDuplicates").nodes().stream().anyMatch(n -> n.data().hasIssue() && n.data().issueId().startsWith("NESTED_LOOP"));
        assertTrue(flagged);
        assertTrue(nested);
    }

    @Test
    void metricsContainCyclomaticComplexity() {
        GraphResponse r = service.graph(Demo.project(), null);
        assertTrue(r.metrics().cyclomaticByMethod().get("Main.uniqueCustomers") >= 3);
        assertEquals(1, r.metrics().cyclomaticByMethod().get("Main.total") - 1);
        assertTrue(r.metrics().linesOfCode() > 100);
    }

    @Test
    void unsupportedStatementBecomesRawNode() {
        String src = "class A { void m() { Runnable r = () -> {}; synchronized (this) { r.run(); } } }";
        GraphResponse r = service.graph(List.of(new ProjectFile("A.java", src)), null);
        List<String> kinds = r.methods().get(0).nodes().stream().map(GraphNode::type).toList();
        assertTrue(kinds.contains("raw"));
        assertTrue(kinds.contains("var"));
    }

    @Test
    void parseErrorReportsLine() {
        JavaParseException e = assertThrows(JavaParseException.class,
                () -> service.graph(List.of(new ProjectFile("A.java", "class A {\n void m( {\n}\n}")), null));
        assertNotNull(e.line());
    }

    @Test
    void loopHasBackEdgeAndIfWithoutElseHasFalseEdge() {
        String src = "class A { int f(int[] a) { int s = 0; for (int x : a) { if (x > 0) { s += x; } s++; } return s; } }";
        MethodGraphResponse g = service.graph(List.of(new ProjectFile("A.java", src)), null).methods().get(0);
        // конец тела цикла (s++) возвращается в заголовок цикла
        assertTrue(g.edges().stream().anyMatch(e -> e.type().equals("back")
                && e.source().endsWith("/s2.b.2") && e.target().endsWith("/s2")), g.edges().toString());
        // if без else: переход к следующему оператору — ветка «Нет»
        assertTrue(g.edges().stream().anyMatch(e -> e.type().equals("false") && e.label().equals("Нет")
                && e.source().endsWith("/s2.b.1") && e.target().endsWith("/s2.b.2")), g.edges().toString());
        // идентификаторы рёбер уникальны
        assertEquals(g.edges().size(), g.edges().stream().map(GraphEdge::id).distinct().count());
    }
}

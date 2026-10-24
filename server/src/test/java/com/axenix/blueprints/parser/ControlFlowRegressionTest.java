package com.axenix.blueprints.parser;

import com.axenix.blueprints.model.Dto.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ControlFlowRegressionTest {
    private MethodGraphResponse graph(String body) {
        return GraphBuilder.build(ProjectModel.parse(List.of(new ProjectFile("A.java",
                "class A { void f(boolean flag) { " + body + " } }"))).methods.get(0));
    }
    private boolean edge(MethodGraphResponse graph, String from, String to) {
        return graph.edges().stream().anyMatch(e -> e.source().equals("A.f/" + from) && e.target().equals("A.f/" + to));
    }
    @Test void bothBranchesJoinAtContinuation() {
        var g = graph("if (flag) { System.out.print(1); } else { System.out.print(2); } System.out.print(3);");
        assertTrue(edge(g, "s1.t.1", "s2"));
        assertTrue(edge(g, "s1.e.1", "s2"));
        assertFalse(edge(g, "s1", "s2"));
    }
    @Test void breakAndContinueUseLoopTargetsAndReturnHasNoOutgoingEdge() {
        var g = graph("while (flag) { if (flag) break; continue; } return;");
        assertTrue(edge(g, "s1.b.1.t.1", "s2"));
        assertTrue(edge(g, "s1.b.2", "s1"));
        assertTrue(g.edges().stream().noneMatch(e -> e.source().equals("A.f/s2")));
    }
}

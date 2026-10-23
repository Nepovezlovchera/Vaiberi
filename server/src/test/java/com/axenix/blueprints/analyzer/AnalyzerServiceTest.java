package com.axenix.blueprints.analyzer;

import com.axenix.blueprints.model.Dto.AnalysisReport;
import com.axenix.blueprints.model.Dto.CodeIssue;
import com.axenix.blueprints.model.Dto.ProjectFile;
import com.axenix.blueprints.support.Demo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyzerServiceTest {

    private final AnalyzerServiceImpl analyzer = new AnalyzerServiceImpl();

    private CodeIssue find(AnalysisReport r, String type, String methodPrefix) {
        return r.issues().stream().filter(i -> i.type().equals(type) && i.nodeId().startsWith(methodPrefix))
                .findFirst().orElseThrow(() -> new AssertionError("нет " + type + " в " + methodPrefix));
    }

    @Test
    void findsQuadraticProblemsInDemo() {
        AnalysisReport r = analyzer.analyze(Demo.project());
        CodeIssue contains = find(r, "COSTLY_CALL_IN_LOOP", "Main.uniqueCustomers");
        assertEquals("O(n²)", contains.complexityBefore());
        assertEquals("O(n)", contains.complexityAfter());
        CodeIssue nested = find(r, "NESTED_LOOP", "Main.findDuplicates");
        assertEquals("O(n²)", nested.complexityBefore());
        assertTrue(nested.lineStart() > 0);
    }

    @Test
    void issueIsBoundToTheSmallestNode() {
        AnalysisReport r = analyzer.analyze(Demo.project());
        CodeIssue contains = find(r, "COSTLY_CALL_IN_LOOP", "Main.uniqueCustomers");
        assertTrue(contains.nodeId().contains("/"), "привязка к узлу, а не к методу: " + contains.nodeId());
    }

    @Test
    void issueIdsAreUnique() {
        AnalysisReport r = analyzer.analyze(Demo.project());
        assertEquals(r.issues().size(), r.issues().stream().map(CodeIssue::id).distinct().count());
    }

    @Test
    void detectsLongMethod() {
        StringBuilder sb = new StringBuilder("class A { void m() {\n");
        for (int i = 0; i < 40; i++) sb.append("  System.out.println(").append(i).append(");\n");
        sb.append("} }");
        AnalysisReport r = analyzer.analyze(List.of(new ProjectFile("A.java", sb.toString())));
        assertTrue(r.issues().stream().anyMatch(i -> i.type().equals("LONG_METHOD")));
    }

    @Test
    void detectsHighCyclomaticComplexity() {
        StringBuilder sb = new StringBuilder("class A { int m(int x) {\n");
        for (int i = 0; i < 12; i++) sb.append("  if (x == ").append(i).append(") return ").append(i).append(";\n");
        sb.append("  return -1;\n} }");
        AnalysisReport r = analyzer.analyze(List.of(new ProjectFile("A.java", sb.toString())));
        assertTrue(r.issues().stream().anyMatch(i -> i.type().equals("HIGH_CYCLOMATIC")));
    }

    @Test
    void detectsDuplicatedMethodsWithRenamedVariables() {
        String src = "class A {\n"
                + " int f(int[] a) {\n int s = 0;\n for (int x : a) {\n s += x;\n }\n return s;\n }\n"
                + " int g(int[] b) {\n int t = 0;\n for (int y : b) {\n t += y;\n }\n return t;\n }\n}";
        AnalysisReport r = analyzer.analyze(List.of(new ProjectFile("A.java", src)));
        assertTrue(r.issues().stream().anyMatch(i -> i.type().equals("DUPLICATION") && i.nodeId().equals("A.g/entry")));
    }

    @Test
    void cleanCodeHasNoIssues() {
        AnalysisReport r = analyzer.analyze(List.of(new ProjectFile("A.java",
                "class A { int sum(int[] a) { int s = 0; for (int x : a) s += x; return s; } }")));
        assertTrue(r.issues().isEmpty());
    }
}

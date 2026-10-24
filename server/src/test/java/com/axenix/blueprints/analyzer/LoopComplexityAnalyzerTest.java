package com.axenix.blueprints.analyzer;

import com.axenix.blueprints.analyzer.LoopComplexityAnalyzer.Finding;
import com.axenix.blueprints.analyzer.LoopComplexityAnalyzer.Type;
import com.axenix.blueprints.parser.ProjectModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopComplexityAnalyzerTest {

    private final LoopComplexityAnalyzer analyzer = new LoopComplexityAnalyzer();

    private List<Finding> run(String body) {
        String src = "import java.util.*; class A { " + body + " }";
        return analyzer.analyze(ProjectModel.parseUnit(ProjectModel.newParser(), "A.java", src));
    }

    @Test
    void nestedForEach_isQuadratic() {
        var f = run("void m(List<Integer> xs) { for (int a : xs) { for (int b : xs) { System.out.println(a + b); } } }");
        assertEquals(1, f.size());
        assertEquals(Type.NESTED_LOOP, f.get(0).type());
        assertEquals("O(n²)", f.get(0).complexity());
    }

    @Test
    void tripleNesting_reportedOnceAsCubic() {
        var f = run("void m(int n) { for (int i=0;i<n;i++) for (int j=0;j<n;j++) for (int k=0;k<n;k++) {} }");
        assertEquals(1, f.size());
        assertEquals(3, f.get(0).degree());
    }

    @Test
    void constantInnerLoop_staysLinear() {
        var f = run("void m(int n) { for (int i=0;i<n;i++) { for (int j=0;j<10;j++) {} } }");
        assertTrue(f.isEmpty());
    }

    @Test
    void listContainsInsideLoop_isFlagged() {
        var f = run("void m(List<String> a, List<String> seen) { for (String s : a) { if (!seen.contains(s)) seen.add(s); } }");
        assertEquals(1, f.size());
        assertEquals(Type.COSTLY_CALL_IN_LOOP, f.get(0).type());
        assertEquals("O(n²)", f.get(0).complexity());
    }

    @Test
    void setContainsInsideLoop_isNotFlagged() {
        var f = run("void m(List<String> a, Set<String> seen) { for (String s : a) { if (!seen.contains(s)) seen.add(s); } }");
        assertTrue(f.isEmpty());
    }

    @Test
    void singleLoop_noFindings() {
        assertTrue(run("void m(int n) { for (int i=0;i<n;i++) {} }").isEmpty());
    }

    @Test void decreasingBoundsAndConstructorsRemainQuadratic() {
        assertEquals("O(n²)", run("A(int n) { for (int i=n;i>0;i--) for (int j=n;j>0;j--) {} }").get(0).complexity());
    }

    @Test void bucketsAreNotAssumedConstantByVariableName() {
        assertEquals("O(n²)", run("void m(List<Integer> xs, Map<Integer,List<Integer>> index) { for(int x:xs) { List<Integer> matches=index.get(0); for(int y:matches) {} } }").get(0).complexity());
    }
}

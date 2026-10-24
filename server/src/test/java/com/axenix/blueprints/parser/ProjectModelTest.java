package com.axenix.blueprints.parser;

import com.axenix.blueprints.analyzer.AnalyzerServiceImpl;
import com.axenix.blueprints.model.Dto.AnalysisReport;
import com.axenix.blueprints.model.Dto.GraphNode;
import com.axenix.blueprints.model.Dto.GraphResponse;
import com.axenix.blueprints.model.Dto.MethodGraphResponse;
import com.axenix.blueprints.model.Dto.ProjectFile;
import com.axenix.blueprints.support.Demo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectModelTest {

    private static List<String> ids(String source) {
        return ProjectModel.parse(List.of(new ProjectFile("A.java", source))).methods.stream()
                .map(ProjectModel.MethodRef::id).toList();
    }

    private static final String SHAPES = """
            package geo;
            interface Shape { double area(); }
            public class Structure {
                double area() { return 0; }
                Shape make() {
                    return new Shape() {
                        public double area() { return 1; }
                    };
                }
                Shape other() {
                    Runnable r = () -> System.out.println("x");
                    return new Shape() {
                        public double area() {
                            Shape inner = new Shape() {
                                public double area() { return 3; }
                            };
                            return inner.area();
                        }
                    };
                }
            }
            """;

    @Test
    void anonymousClassMethodGetsDistinctClassSegment() {
        ProjectModel pm = ProjectModel.parse(List.of(new ProjectFile("A.java", SHAPES)));
        List<String> ids = pm.methods.stream().map(ProjectModel.MethodRef::id).toList();
        assertTrue(ids.contains("geo.Structure.area"), ids.toString());
        assertTrue(ids.contains("geo.Structure$1.area"), ids.toString());
        assertTrue(ids.contains("geo.Structure$2.area"), ids.toString());
        assertFalse(ids.contains("geo.Structure.area#2"), ids.toString());
        assertEquals("geo.Structure$1", pm.method("geo.Structure$1.area").className());
    }

    @Test
    void nestedAnonymousClassIsNumberedWithinItsEnclosingAnonymousClass() {
        List<String> ids = ids(SHAPES);
        assertTrue(ids.contains("geo.Structure$2$1.area"), ids.toString());
        for (String id : ids) assertFalse(id.contains("/"), id);
    }

    @Test
    void lambdaDoesNotCreateMethod() {
        List<String> ids = ids(SHAPES);
        assertEquals(List.of("geo.Shape.area", "geo.Structure.area", "geo.Structure.make", "geo.Structure$1.area",
                "geo.Structure.other", "geo.Structure$2.area", "geo.Structure$2$1.area"), ids);
    }

    @Test
    void anonymousClassInConstructorArgumentBelongsToOuterClass() {
        List<String> ids = ids("""
                class A {
                    void run() {
                        Object o = new Thread(new Runnable() { public void run() {} }) {
                            public void start() {}
                        };
                    }
                }
                """);
        // порядок по исходнику: Thread-подкласс начинается первым (A$1), Runnable в его аргументах — A$2;
        // Runnable не вложен в A$1, т.к. аргументы конструктора не входят в тело анонимного класса
        assertEquals(java.util.Set.of("A.run", "A$1.start", "A$2.run"), java.util.Set.copyOf(ids));
    }

    @Test
    void localClassInsideMethodIsNamedByItsTypeName() {
        List<String> ids = ids("""
                class A {
                    void run() {
                        class Local { int f() { return 1; } }
                        new Local().f();
                    }
                }
                """);
        assertEquals(List.of("A.run", "A.Local.f"), ids);
    }

    @Test
    void overloadsStillGetSuffix() {
        List<String> ids = ids("""
                class A {
                    void f() {}
                    void f(int x) {}
                    void f(String s) {}
                }
                """);
        assertEquals(List.of("A.f", "A.f#2", "A.f#3"), ids);
    }

    private static final String POINT = """
            package geo;
            public record Point(int x, int y) {
                Point {
                    if (x < 0) throw new IllegalArgumentException("x");
                    if (y < 0) throw new IllegalArgumentException("y");
                }
                Point(int x) { this(x, 0); }
                int sum() { return x + y; }
            }
            """;

    @Test
    void recordCompactConstructorIsIndexed() {
        ProjectModel pm = ProjectModel.parse(List.of(new ProjectFile("Point.java", POINT)));
        List<String> ids = pm.methods.stream().map(ProjectModel.MethodRef::id).toList();
        assertEquals(List.of("geo.Point.Point", "geo.Point.Point#2", "geo.Point.sum"), ids);
        assertNotNull(pm.method("geo.Point.Point").body());
        assertEquals(2, pm.method("geo.Point.Point").body().getStatements().size());
    }

    @Test
    void compactConstructorHasGraph() {
        ParseServiceImpl service = new ParseServiceImpl(new AnalyzerServiceImpl());
        GraphResponse r = service.graph(List.of(new ProjectFile("Point.java", POINT)), "geo.Point.Point");
        assertEquals(1, r.methods().size());
        MethodGraphResponse g = r.methods().get(0);
        GraphNode entry = g.nodes().stream().filter(n -> n.type().equals("method")).findFirst().orElseThrow();
        assertTrue(entry.data().label().contains("Point"), entry.data().label());
        assertTrue(g.methodSignature().contains("Point"), g.methodSignature());
        assertEquals(3, entry.data().lineStart());
        assertEquals(2, g.nodes().stream().filter(n -> n.type().equals("condition")).count());
    }

    @Test
    void analysisHandlesCompactConstructors() {
        // два одинаковых компактных конструктора (≥ 4 строк) — проверяем, что анализ (в т.ч. дубли) не падает
        String src = """
                record P(int x, int y) {
                    P {
                        if (x < 0) throw new IllegalArgumentException("x");
                        if (y < 0) throw new IllegalArgumentException("y");
                    }
                }
                record Q(int a, int b) {
                    Q {
                        if (a < 0) throw new IllegalArgumentException("x");
                        if (b < 0) throw new IllegalArgumentException("y");
                    }
                }
                """;
        AnalysisReport rep = new AnalyzerServiceImpl().analyze(List.of(new ProjectFile("P.java", src)));
        assertEquals(2, rep.metrics().methodCount());
        assertTrue(rep.metrics().cyclomaticByMethod().containsKey("P.P"), rep.metrics().cyclomaticByMethod().toString());
        assertEquals(3, rep.metrics().cyclomaticByMethod().get("P.P"));
        assertTrue(rep.issues().stream().anyMatch(i -> i.type().equals("DUPLICATION") && i.description().contains("Q.Q")),
                rep.issues().toString());
    }

    @Test
    void demoMethodIdsUnchanged() {
        List<String> ids = ProjectModel.parse(Demo.project()).methods.stream().map(ProjectModel.MethodRef::id).toList();
        assertEquals(List.of("Main.Order.Order", "Main.InvalidOrderException.InvalidOrderException", "Main.Stats.Stats",
                "Main.Stats.of", "Main.parseOrder", "Main.findDuplicates", "Main.uniqueCustomers", "Main.total",
                "Main.category", "Main.totalsByCustomer", "Main.printReport", "Main.main"), ids);
    }
}

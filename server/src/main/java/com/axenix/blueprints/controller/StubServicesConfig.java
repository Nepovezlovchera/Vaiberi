package com.axenix.blueprints.controller;

import com.axenix.blueprints.model.Dto.*;
import com.axenix.blueprints.model.Services.*;
import com.axenix.blueprints.sandbox.ReportStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Map;

/**
 * Заглушки для фронтенда: запуск с --spring.profiles.active=stub.
 * Возвращают мок-граф (метод Main.processOrders с циклом и условием), одну проблему и успешный отчёт.
 */
@Configuration
@Profile("stub")
public class StubServicesConfig {

    private static final String M = "Main.processOrders";

    private static NodeData nd(String label, int from, int to, String loopType, String cond, boolean issue) {
        return new NodeData(label, label, from, to, 1, 80, issue, issue ? "NESTED_LOOP:" + M + "/s3" : null,
                issue ? List.of("NESTED_LOOP:" + M + "/s3") : List.of(), loopType, cond, null);
    }

    private static ComplexityMetric metrics() {
        return new ComplexityMetric("O(n²)", "O(n)", "до 5.2x", 1, 4, 14, 1, Map.of(M, 4));
    }

    private static GraphResponse mockGraph() {
        List<GraphNode> nodes = List.of(
                new GraphNode(M + "/entry", null, "method", nd("public List<Order> processOrders(List<Order> orders)", 1, 14, null, null, false)),
                new GraphNode(M + "/s1", M + "/entry", "var", nd("List<Order> result = new ArrayList<>();", 2, 2, null, null, false)),
                new GraphNode(M + "/s2", M + "/entry", "condition", nd("if (orders == null || orders.isEmpty())", 3, 5, null, "orders == null || orders.isEmpty()", false)),
                new GraphNode(M + "/s2.t.1", M + "/s2", "return", nd("return result;", 4, 4, null, null, false)),
                new GraphNode(M + "/s3", M + "/entry", "loop", nd("for (Order order : orders)", 6, 12, "foreach", "orders", true)),
                new GraphNode(M + "/s3.b.1", M + "/s3", "call", nd("result.add(order);", 7, 7, null, null, false)),
                new GraphNode(M + "/s4", M + "/entry", "return", nd("return result;", 13, 13, null, null, false)));
        List<GraphEdge> edges = List.of(
                new GraphEdge("e_entry_s1", M + "/entry", M + "/s1", "next", null),
                new GraphEdge("e_s1_s2", M + "/s1", M + "/s2", "next", null),
                new GraphEdge("e_s2_s2.t.1", M + "/s2", M + "/s2.t.1", "true", "Да"),
                new GraphEdge("e_s2_s3", M + "/s2", M + "/s3", "next", null),
                new GraphEdge("e_s3_s3.b.1", M + "/s3", M + "/s3.b.1", "body", "Тело цикла"),
                new GraphEdge("e_s3_s4", M + "/s3", M + "/s4", "next", null));
        return new GraphResponse(List.of(new MethodGraphResponse("Main", M, "public List<Order> processOrders(List<Order> orders)",
                "Main.java", 1, 14, nodes, edges)), metrics());
    }

    @Bean GraphService graphService() {
        return (files, methodId) -> mockGraph();
    }
    @Bean GraphEditService graphEditService() {
        return req -> new MutateResponse(req.files(), mockGraph(), false, "");
    }
    @Bean AnalyzerService analyzerService() {
        return files -> new AnalysisReport(List.of(new CodeIssue("NESTED_LOOP:" + M + "/s3", M + "/s3", "NESTED_LOOP",
                "Вложенность O(n²)", "HIGH", 6, 12, "Main.java", "Вложенный цикл выполняет поиск O(n) на каждой итерации",
                "Рост времени выполнения при росте входных данных", "Индексировать элементы в Map/Set по ключу id", null,
                "O(n²)", "O(n)")), metrics());
    }
    @Bean OptimizerService optimizerService() {
        return req -> new OptimizeResponse(req.files(), "TEMPLATE", "stub");
    }
    @Bean DecomposeService decomposeService() {
        return req -> new DecomposeResponse(req.files(), List.of(), "stub");
    }
    @Bean RunService runService() {
        return req -> new RunResponse(true, "", "stub\n", "", 0, false, 1);
    }
    @Bean VerifyService verifyService() {
        return (req, log) -> {
            log.accept("[00:00:00] stub: проверка пропущена");
            return new VerificationReport(true, true, 1240, 238, "5.2x", "O(n²)", "O(n)",
                    List.of(new TestCaseResult("stub", "PASSED", 1, null)), List.of("stub"), "", 1, 1, "2026-10-25T07:45:00Z");
        };
    }
    @Bean ReportStore reportStore() {
        return new ReportStore(null);
    }
}

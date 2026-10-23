package com.axenix.blueprints.controller;

import com.axenix.blueprints.model.Dto.*;
import com.axenix.blueprints.model.Services.*;
import com.axenix.blueprints.sandbox.ReportStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api")
@CrossOrigin // на хакатоне достаточно; для прода заменить на конкретный origin
public class ApiController {

    private final GraphService graphService;
    private final GraphEditService graphEditService;
    private final AnalyzerService analyzerService;
    private final OptimizerService optimizerService;
    private final DecomposeService decomposeService;
    private final VerifyService verifyService;
    private final RunService runService;
    private final ReportStore reports;
    private final ExecutorService streams = Executors.newCachedThreadPool();

    public ApiController(GraphService graphService, GraphEditService graphEditService,
                         AnalyzerService analyzerService, OptimizerService optimizerService,
                         DecomposeService decomposeService, VerifyService verifyService,
                         RunService runService, ReportStore reports) {
        this.graphService = graphService;
        this.graphEditService = graphEditService;
        this.analyzerService = analyzerService;
        this.optimizerService = optimizerService;
        this.decomposeService = decomposeService;
        this.verifyService = verifyService;
        this.runService = runService;
        this.reports = reports;
    }

    @GetMapping("/health")
    public Map<String, String> health() { return Map.of("status", "ok"); }

    /** Java → граф (по графу на метод) + метрики. */
    @PostMapping("/graph")
    public GraphResponse graph(@RequestBody GraphRequest req) {
        return graphService.graph(req.files(), req.methodId());
    }

    /** Правка графа → новый код + перестроенный граф (с проверкой компиляции). */
    @PostMapping("/graph/mutate")
    public MutateResponse mutate(@RequestBody MutateRequest req) {
        return graphEditService.mutate(req);
    }

    @PostMapping("/analysis")
    public AnalysisReport analysis(@RequestBody FilesRequest req) {
        return analyzerService.analyze(req.files());
    }

    @PostMapping("/optimize")
    public OptimizeResponse optimize(@RequestBody OptimizeRequest req) {
        return optimizerService.optimize(req);
    }

    @PostMapping("/decompose")
    public DecomposeResponse decompose(@RequestBody DecomposeRequest req) {
        return decomposeService.decompose(req);
    }

    /** Quality Gate, синхронный вариант. */
    @PostMapping("/verify")
    public VerificationReport verify(@RequestBody VerifyRequest req) {
        return verifyService.verify(req);
    }

    /**
     * Quality Gate с потоком логов (SSE): события «log» (строка терминала), в конце «complete» с JSON отчёта
     * или «error» с текстом ошибки.
     */
    @PostMapping(value = "/verify/stream", produces = "text/event-stream")
    public SseEmitter verifyStream(@RequestBody VerifyRequest req) {
        SseEmitter emitter = new SseEmitter(180_000L);
        streams.submit(() -> {
            try {
                VerificationReport report = verifyService.verify(req, line -> {
                    try {
                        emitter.send(SseEmitter.event().name("log").data(line));
                    } catch (IOException ignored) {
                        // клиент закрыл поток: проверка всё равно доработает и сохранит отчёт
                    }
                });
                emitter.send(SseEmitter.event().name("complete").data(report, MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(String.valueOf(e.getMessage())));
                } catch (IOException ignored) {
                    // нечего сообщать закрытому потоку
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /** Последний сохранённый verification_report.json. */
    @GetMapping(value = "/verify/report", produces = "application/json")
    public String report() {
        return reports.load().orElseThrow(() -> new NoSuchElementException("Отчёт ещё не создан: запустите /api/verify"));
    }

    /** Скомпилировать и запустить проект (консоль). */
    @PostMapping("/run")
    public RunResponse run(@RequestBody RunRequest req) {
        return runService.run(req);
    }
}

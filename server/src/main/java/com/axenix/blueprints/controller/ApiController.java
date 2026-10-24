package com.axenix.blueprints.controller;

import com.axenix.blueprints.model.Dto.*;
import com.axenix.blueprints.model.Services.*;
import com.axenix.blueprints.sandbox.InteractiveRunService;
import com.axenix.blueprints.sandbox.ReportStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
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
    private final Optional<InteractiveRunService> interactive;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ReportStore reports;
    private final ExecutorService streams = Executors.newCachedThreadPool();

    public ApiController(GraphService graphService, GraphEditService graphEditService,
                         AnalyzerService analyzerService, OptimizerService optimizerService,
                         DecomposeService decomposeService, VerifyService verifyService,
                         RunService runService, ReportStore reports,
                         Optional<InteractiveRunService> interactive) {
        this.graphService = graphService;
        this.graphEditService = graphEditService;
        this.analyzerService = analyzerService;
        this.optimizerService = optimizerService;
        this.decomposeService = decomposeService;
        this.verifyService = verifyService;
        this.runService = runService;
        this.interactive = interactive;
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

    // ---------- интерактивная консоль: вывод по SSE, ввод отдельными запросами ----------

    private static String json(Object v) {
        try {
            return JSON.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            return "\"\"";
        }
    }

    private InteractiveRunService interactiveService() {
        return interactive.orElseThrow(() -> new IllegalStateException("Интерактивный запуск недоступен в этом профиле"));
    }

    /**
     * Запуск с интерактивным вводом. Поток событий: «session» (id), «out»/«err» (JSON-строка с фрагментом вывода,
     * без обязательного перевода строки), «compile-error» (JSON-строка), «exit» ({exitCode, millis, reason}), «error».
     * Ввод: POST /run/session/{id}/stdin, остановка: DELETE /run/session/{id}.
     */
    @PostMapping(value = "/run/session", produces = "text/event-stream")
    public SseEmitter runSession(@RequestBody RunRequest req) {
        InteractiveRunService svc = interactiveService();
        SseEmitter emitter = new SseEmitter(15 * 60_000L);
        AtomicReference<String> sid = new AtomicReference<>();
        Runnable kill = () -> svc.stop(sid.get());
        emitter.onCompletion(kill);
        emitter.onTimeout(kill);
        emitter.onError(t -> kill.run());
        streams.submit(() -> {
            try {
                svc.run(req, new InteractiveRunService.Listener() {
                    private void send(String name, String data) {
                        try {
                            synchronized (emitter) {
                                emitter.send(SseEmitter.event().name(name).data(data));
                            }
                        } catch (IOException | IllegalStateException e) {
                            kill.run(); // клиент ушёл: программу больше никто не читает
                        }
                    }
                    @Override public void onSession(String id) { sid.set(id); send("session", id); }
                    @Override public void onCompileError(String output) { send("compile-error", json(output)); }
                    @Override public void onOut(String text) { send("out", json(text)); }
                    @Override public void onErr(String text) { send("err", json(text)); }
                    @Override public void onExit(int exitCode, long millis, String reason) {
                        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                        m.put("exitCode", exitCode);
                        m.put("millis", millis);
                        m.put("reason", reason);
                        send("exit", json(m));
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                try {
                    synchronized (emitter) {
                        emitter.send(SseEmitter.event().name("error").data(json(String.valueOf(e.getMessage()))));
                    }
                } catch (IOException | IllegalStateException ignored) {
                    // нечего сообщать закрытому потоку
                }
                emitter.complete();
            }
        });
        return emitter;
    }

    /** Ввод в запущенную программу (текст с переводом строки) и/или признак конца ввода. */
    @PostMapping("/run/session/{id}/stdin")
    public Map<String, Object> runSessionStdin(@PathVariable("id") String id, @RequestBody StdinRequest in) {
        interactiveService().sendInput(id, in.text(), in.eof());
        return Map.of("ok", true);
    }

    /** Остановить программу. */
    @DeleteMapping("/run/session/{id}")
    public Map<String, Object> runSessionStop(@PathVariable("id") String id) {
        interactiveService().stop(id);
        return Map.of("ok", true);
    }
}

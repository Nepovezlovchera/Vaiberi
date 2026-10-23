package com.axenix.blueprints.sandbox;

import com.axenix.blueprints.analyzer.AnalyzerServiceImpl;
import com.axenix.blueprints.model.Dto.CompareMode;
import com.axenix.blueprints.model.Dto.ProjectFile;
import com.axenix.blueprints.model.Dto.TestCase;
import com.axenix.blueprints.model.Dto.TestCaseResult;
import com.axenix.blueprints.model.Dto.VerificationReport;
import com.axenix.blueprints.model.Dto.VerifyRequest;
import com.axenix.blueprints.model.Services.VerifyService;
import com.axenix.blueprints.parser.ProjectModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Quality Gate (спецификация, раздел 7). Компилирует оригинал и изменённую версию, прогоняет оба на одних тестах
 * и сравнивает stdout, stderr (без стека) и код выхода. Если у теста нет expectedStdout, эталоном служит вывод оригинала.
 * Ход проверки пишется в log (SSE-терминал) и попадает в отчёт; отчёт сохраняется как verification_report.json.
 */
public class VerifyServiceImpl implements VerifyService {

    private static final Semaphore SLOTS = new Semaphore(2);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final CompilerService compiler;
    private final ProcessRunner runner;
    private final AnalyzerServiceImpl analyzer; // может быть null: тогда сложность не оценивается
    private final ReportStore reports;

    public VerifyServiceImpl(CompilerService compiler, ProcessRunner runner, AnalyzerServiceImpl analyzer, ReportStore reports) {
        this.compiler = compiler;
        this.runner = runner;
        this.analyzer = analyzer;
        this.reports = reports;
    }

    public VerifyServiceImpl(CompilerService compiler, ProcessRunner runner) {
        this(compiler, runner, null, new ReportStore(null));
    }

    @Override
    public VerificationReport verify(VerifyRequest req, Consumer<String> sink) {
        List<String> logs = new ArrayList<>();
        Consumer<String> log = text -> {
            String line = "[" + LocalTime.now().format(CLOCK) + "] " + text;
            logs.add(line);
            sink.accept(line);
        };
        List<TestCase> tests = req.tests() == null || req.tests().isEmpty()
                ? List.of(new TestCase("default", "", null)) : req.tests();
        CompareMode mode = req.mode() == null ? CompareMode.EXACT : req.mode();
        try {
            if (!SLOTS.tryAcquire(60, TimeUnit.SECONDS)) throw new IllegalStateException("Сервер занят проверками, повторите позже");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Проверка прервана", e);
        }
        Path dirOrig = null;
        Path dirMod = null;
        try {
            dirOrig = Files.createTempDirectory("bp-orig-");
            dirMod = Files.createTempDirectory("bp-mod-");
            String mainOrig = MainClassFinder.find(req.original(), req.mainClass());
            String mainMod = MainClassFinder.find(req.modified(), req.mainClass());

            log.accept("Компиляция исходной версии...");
            CompilerService.CompileResult co = compiler.compile(req.original(), dirOrig);
            if (!co.ok()) return finish(failedBuild("Оригинал не компилируется:\n" + co.output(), tests, logs, log), req);
            log.accept("Компиляция успешна ✓");
            log.accept("Компиляция изменённой версии...");
            CompilerService.CompileResult cm = compiler.compile(req.modified(), dirMod);
            if (!cm.ok()) return finish(failedBuild("Изменённая версия не компилируется:\n" + cm.output(), tests, logs, log), req);
            log.accept("Компиляция успешна ✓. Запуск тестов (" + tests.size() + ")...");

            List<TestCaseResult> results = new ArrayList<>();
            long origMs = 0;
            long modMs = 0;
            int i = 0;
            for (TestCase t : tests) {
                i++;
                long[] times = new long[2];
                TestCaseResult r = runOne(t, mode, co.classesDir(), mainOrig, cm.classesDir(), mainMod, times);
                origMs += times[0];
                modMs += times[1];
                results.add(r);
                log.accept("Тест " + i + "/" + tests.size() + " (" + t.name() + "): " + r.status() + " (" + r.durationMs() + " мс)"
                        + (r.errorDetails() == null ? "" : " — " + r.errorDetails()));
            }
            int passed = (int) results.stream().filter(r -> r.status().equals("PASSED")).count();
            boolean ok = passed == results.size();
            log.accept(ok ? "Все тесты пройдены: " + passed + " из " + results.size() + " (100%). Поведение совпадает!"
                    : "Поведение НЕ совпадает: пройдено " + passed + " из " + results.size());
            String before = complexity(req.original());
            String after = complexity(req.modified());
            VerificationReport report = new VerificationReport(ok, ok, origMs, modMs, speedup(origMs, modMs), before, after,
                    results, logs, "", passed, results.size(), Instant.now().toString());
            return finish(report, req);
        } catch (IOException e) {
            throw new IllegalStateException("Ошибка проверки: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Проверка прервана", e);
        } finally {
            RunServiceImpl.deleteQuietly(dirOrig);
            RunServiceImpl.deleteQuietly(dirMod);
            SLOTS.release();
        }
    }

    private VerificationReport finish(VerificationReport r, VerifyRequest req) {
        reports.save(r);
        return r;
    }

    private String complexity(List<ProjectFile> files) {
        if (analyzer == null) return null;
        try {
            return analyzer.worstComplexity(ProjectModel.parse(files));
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String speedup(long orig, long mod) {
        if (orig <= 0 || mod <= 0) return "n/a";
        return String.format(Locale.ROOT, "%.1fx", (double) orig / mod);
    }

    private TestCaseResult runOne(TestCase t, CompareMode mode, Path classesOrig, String mainOrig,
                                  Path classesMod, String mainMod, long[] times) throws IOException, InterruptedException {
        ProcessRunner.RunResult o = runner.run(classesOrig, mainOrig, t.stdin());
        times[0] = o.millis();
        if (o.timedOut()) return new TestCaseResult(t.name(), "TIMEOUT", o.millis(), "Оригинал не завершился за отведённое время");
        ProcessRunner.RunResult m = runner.run(classesMod, mainMod, t.stdin());
        times[1] = m.millis();
        if (m.timedOut()) return new TestCaseResult(t.name(), "TIMEOUT", m.millis(), "Изменённая версия не завершилась за отведённое время");

        String expectedOut = t.expectedStdout() != null ? t.expectedStdout() : o.stdout();
        String d = OutputComparator.diff(OutputComparator.normalize(expectedOut, mode),
                OutputComparator.normalize(m.stdout(), mode));
        String errDiff = OutputComparator.diff(
                OutputComparator.normalize(OutputComparator.stripStackTrace(o.stderr()), mode),
                OutputComparator.normalize(OutputComparator.stripStackTrace(m.stderr()), mode));
        StringBuilder sb = new StringBuilder();
        if (d != null) sb.append("stdout, ").append(d);
        if (errDiff != null) sb.append(sb.length() > 0 ? "; " : "").append("stderr, ").append(errDiff);
        if (o.exitCode() != m.exitCode()) {
            sb.append(sb.length() > 0 ? "; " : "").append("код выхода: было ").append(o.exitCode()).append(", стало ").append(m.exitCode());
        }
        return sb.length() == 0
                ? new TestCaseResult(t.name(), "PASSED", m.millis(), null)
                : new TestCaseResult(t.name(), "FAILED", m.millis(), sb.toString());
    }

    private static VerificationReport failedBuild(String output, List<TestCase> tests, List<String> logs, Consumer<String> log) {
        log.accept("Сборка не удалась: " + output.lines().findFirst().orElse(""));
        List<TestCaseResult> r = new ArrayList<>();
        for (TestCase t : tests) r.add(new TestCaseResult(t.name(), "COMPILE_ERROR", 0, "Сборка не удалась"));
        return new VerificationReport(false, false, 0, 0, "n/a", null, null, r, logs, output, 0, r.size(), Instant.now().toString());
    }
}

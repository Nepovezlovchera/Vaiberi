package com.axenix.blueprints.sandbox;

import com.axenix.blueprints.model.Dto.RunRequest;
import com.axenix.blueprints.model.Dto.RunResponse;
import com.axenix.blueprints.model.Services.RunService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** Кнопка «Запуск»: компиляция + запуск в отдельном процессе. */
public class RunServiceImpl implements RunService {

    private final CompilerService compiler;
    private final ProcessRunner runner;

    public RunServiceImpl(CompilerService compiler, ProcessRunner runner) {
        this.compiler = compiler;
        this.runner = runner;
    }

    @Override
    public RunResponse run(RunRequest req) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("bp-run-");
            String main = MainClassFinder.find(req.files(), req.mainClass());
            CompilerService.CompileResult c = compiler.compile(req.files(), dir);
            if (!c.ok()) return new RunResponse(false, c.output(), "", "", -1, false, 0);
            ProcessRunner.RunResult r = runner.run(c.classesDir(), main, req.stdin());
            return new RunResponse(true, "", r.stdout(), r.stderr(), r.exitCode(), r.timedOut(), r.millis());
        } catch (IOException e) {
            throw new IllegalStateException("Ошибка запуска: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Запуск прерван", e);
        } finally {
            deleteQuietly(dir);
        }
    }

    public static void deleteQuietly(Path dir) {
        if (dir == null) return;
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // временные файлы подчистит ОС
        }
    }
}

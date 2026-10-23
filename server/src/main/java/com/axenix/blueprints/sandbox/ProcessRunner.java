package com.axenix.blueprints.sandbox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Запуск скомпилированного кода в ОТДЕЛЬНОМ процессе JVM: ограничение кучи, таймаут, kill,
 * ограничение размера вывода. System.exit или бесконечный цикл пользователя сервер не заденут.
 *
 * Сетевая изоляция на уровне процесса здесь не делается: изоляцию даёт контейнер (см. docker-compose).
 */
public class ProcessRunner {

    public record RunResult(String stdout, String stderr, int exitCode, boolean timedOut, long millis) {}

    private static final int MAX_OUTPUT_BYTES = 1_000_000;

    private final long timeoutMs;
    private final String heap;

    public ProcessRunner(long timeoutMs, String heap) {
        this.timeoutMs = timeoutMs;
        this.heap = heap;
    }

    public RunResult run(Path classesDir, String mainClass, String stdin) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-Xmx" + heap);
        cmd.add("-Xss2m");
        cmd.add("-XX:+UseSerialGC");
        cmd.add("-Djava.awt.headless=true");
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Dstdout.encoding=UTF-8");
        cmd.add("-Dstderr.encoding=UTF-8");
        cmd.add("-cp");
        cmd.add(classesDir.toString());
        cmd.add(mainClass);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(classesDir.toFile());
        // чтобы чужие JVM-опции не попадали в stderr пользовательской программы
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        pb.environment().remove("_JAVA_OPTIONS");
        pb.environment().remove("JDK_JAVA_OPTIONS");

        long t0 = System.nanoTime();
        Process p = pb.start();
        Capture out = new Capture(p.getInputStream());
        Capture err = new Capture(p.getErrorStream());
        out.start();
        err.start();
        Thread in = new Thread(() -> {
            try (OutputStream os = p.getOutputStream()) {
                if (stdin != null) os.write(stdin.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // программа могла завершиться, не дочитав ввод
            }
        });
        in.setDaemon(true);
        in.start();

        boolean finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        if (!finished) {
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
            p.waitFor(2, TimeUnit.SECONDS);
        }
        out.join(1000);
        err.join(1000);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        int code = finished ? p.exitValue() : -1;
        return new RunResult(out.text(), err.text(), code, !finished, ms);
    }

    private static final class Capture extends Thread {
        private final InputStream in;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

        Capture(InputStream in) {
            this.in = in;
            setDaemon(true);
        }

        @Override
        public void run() {
            byte[] chunk = new byte[8192];
            try {
                int n;
                while ((n = in.read(chunk)) != -1) {
                    synchronized (buf) {
                        if (buf.size() < MAX_OUTPUT_BYTES) buf.write(chunk, 0, Math.min(n, MAX_OUTPUT_BYTES - buf.size()));
                    }
                }
            } catch (IOException ignored) {
                // поток закрыт при kill
            }
        }

        String text() {
            synchronized (buf) {
                return buf.toString(StandardCharsets.UTF_8);
            }
        }
    }
}

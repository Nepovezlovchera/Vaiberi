package com.axenix.blueprints.sandbox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Запуск скомпилированного кода в ОТДЕЛЬНОМ процессе JVM: ограничение кучи, таймаут, kill,
 * ограничение размера вывода. System.exit или бесконечный цикл пользователя сервер не заденут.
 *
 * Java 21 policy denies network, process execution, environment access and files outside the job.
 * Compose additionally runs this class in a dedicated, unprivileged, internal-network worker.
 */
public class ProcessRunner {
    private static final java.util.concurrent.Semaphore SLOTS = new java.util.concurrent.Semaphore(4);

    public record RunResult(String stdout, String stderr, int exitCode, boolean timedOut, long millis,
                            boolean outputTruncated) {}

    private static final int MAX_OUTPUT_BYTES = 1_000_000;

    private final long timeoutMs;
    private final String heap;

    public ProcessRunner(long timeoutMs, String heap) {
        this.timeoutMs = timeoutMs;
        this.heap = heap;
    }

    public RunResult run(Path classesDir, String mainClass, String stdin) throws IOException, InterruptedException {
        if (!SLOTS.tryAcquire(1, TimeUnit.SECONDS)) throw new IllegalStateException("Сервер занят запусками");
        try {
            return runIsolated(classesDir, mainClass, stdin);
        } finally {
            SLOTS.release();
        }
    }

    private RunResult runIsolated(Path classesDir, String mainClass, String stdin) throws IOException, InterruptedException {
        classesDir = classesDir.toAbsolutePath().normalize();
        String classesUrl = classesDir.toUri().toASCIIString();
        if (!classesUrl.endsWith("/")) classesUrl += "/";
        Path policy = classesDir.resolveSibling("runner.policy");
        String permissionPath = classesDir.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        Files.writeString(policy, "grant codeBase \"" + classesUrl + "-\" {\n"
                + "permission java.io.FilePermission \"" + permissionPath + java.io.File.separator.replace("\\", "\\\\") + "-\", \"read,write,delete\";\n"
                + "permission java.util.PropertyPermission \"*\", \"read\";\n"
                + "permission java.lang.RuntimePermission \"exitVM.*\";\n"
                + "};\n", StandardCharsets.UTF_8);
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-Xmx" + heap);
        cmd.add("-Xss2m");
        cmd.add("-XX:ActiveProcessorCount=2");
        cmd.add("-Djava.security.manager=default");
        cmd.add("-Djava.security.policy==" + policy.toUri());
        cmd.add("-XX:+UseSerialGC");
        cmd.add("-Djava.awt.headless=true");
        cmd.add("-Djava.io.tmpdir=" + classesDir);
        cmd.add("-Duser.home=" + classesDir);
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Dstdin.encoding=UTF-8");
        cmd.add("-Dstdout.encoding=UTF-8");
        cmd.add("-Dstderr.encoding=UTF-8");
        cmd.add("-Dsun.stdout.encoding=UTF-8");
        cmd.add("-Dsun.stderr.encoding=UTF-8");
        cmd.add("-Dsun.jnu.encoding=UTF-8");
        cmd.add("-cp");
        cmd.add(classesDir.toString());
        cmd.add(mainClass);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(classesDir.toFile());
        // чтобы чужие JVM-опции не попадали в stderr пользовательской программы
        // Do not inherit credentials, proxy settings, or arbitrary JVM options from the server.
        String javaHome = System.getProperty("java.home");
        pb.environment().clear();
        pb.environment().put("PATH", Path.of(javaHome, "bin").toString());
        if (System.getenv("SystemRoot") != null) pb.environment().put("SystemRoot", System.getenv("SystemRoot"));
        pb.environment().put("JAVA_HOME", javaHome);
        pb.environment().put("LANG", "C.UTF-8");
        pb.environment().put("LC_ALL", "C.UTF-8");
        pb.environment().put("HOME", classesDir.toString());

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

        boolean finished;
        try {
            finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
            throw e;
        }
        if (!finished) {
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
            p.waitFor(2, TimeUnit.SECONDS);
        }
        p.descendants().forEach(ProcessHandle::destroyForcibly);
        out.join(1000);
        err.join(1000);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        int code = finished ? p.exitValue() : -1;
        String stderr = err.text().replace("WARNING: A command line option has enabled the Security Manager\n", "")
                .replace("WARNING: The Security Manager is deprecated and will be removed in a future release\n", "");
        return new RunResult(out.text(), stderr, code, !finished, ms, out.truncated() || err.truncated());
    }

    private static final class Capture extends Thread {
        private final InputStream in;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        private volatile boolean truncated;

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
                        int remaining = MAX_OUTPUT_BYTES - buf.size();
                        if (remaining > 0) buf.write(chunk, 0, Math.min(n, remaining));
                        if (n > Math.max(remaining, 0)) truncated = true;
                    }
                }
            } catch (IOException ignored) {
                // поток закрыт при kill
            }
        }

        String text() {
            synchronized (buf) {
                // \u041D\u0430 Windows println \u043F\u0438\u0448\u0435\u0442 \r\n: \u043F\u0440\u0438\u0432\u043E\u0434\u0438\u043C \u043A \n, \u0447\u0442\u043E\u0431\u044B \u0432\u044B\u0432\u043E\u0434 \u043D\u0435 \u0437\u0430\u0432\u0438\u0441\u0435\u043B \u043E\u0442 \u041E\u0421 \u0441\u0435\u0440\u0432\u0435\u0440\u0430
                return buf.toString(StandardCharsets.UTF_8).replace("\uFEFF", "").replace("\r\n", "\n");
            }
        }

        boolean truncated() { return truncated; }
    }
}

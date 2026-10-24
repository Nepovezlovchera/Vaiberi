package com.axenix.blueprints.sandbox;

import com.axenix.blueprints.model.Dto.ProjectFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Compilation runs outside the API JVM and can be forcibly stopped without leaving a compiler thread behind. */
public class CompilerService {
    public record CompileResult(boolean ok, String output, Path classesDir) {}
    private static final Semaphore SLOTS = new Semaphore(2);

    public CompileResult compile(List<ProjectFile> files, Path workDir) throws IOException {
        boolean acquired = false;
        Process process = null;
        try {
            acquired = SLOTS.tryAcquire(1, TimeUnit.SECONDS);
            if (!acquired) return new CompileResult(false, "COMPILATION_BUSY", workDir.resolve("classes"));
            Path src = workDir.resolve("src").toAbsolutePath().normalize();
            Path out = workDir.resolve("classes").toAbsolutePath().normalize();
            Files.createDirectories(src);
            Files.createDirectories(out);
            List<String> args = new ArrayList<>(List.of("-encoding", "UTF-8", "-d", quote(out),
                    "-classpath", quote(out), "-proc:none", "-Xlint:none", "-Xmaxerrs", "100"));
            for (ProjectFile file : files) {
                if (file == null || file.path() == null || file.source() == null)
                    throw new IllegalArgumentException("У файла нет path или source");
                Path target = src.resolve(file.path().replace('\\', '/')).normalize();
                if (file.path().isBlank() || !target.startsWith(src) || !file.path().endsWith(".java"))
                    throw new IllegalArgumentException("Недопустимый путь файла: " + file.path());
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.source(), StandardCharsets.UTF_8);
                args.add(quote(target));
            }
            Path argFile = workDir.resolve("javac.args").toAbsolutePath();
            Files.write(argFile, args, StandardCharsets.UTF_8);
            Path log = workDir.resolve("javac.log");
            ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "javac").toString(),
                    "-J-Xmx128m", "-J-XX:ActiveProcessorCount=2", "@" + argFile);
            pb.environment().clear();
            // Windows needs SystemRoot to locate native runtime libraries.
            String systemRoot = System.getenv("SystemRoot");
            if (systemRoot != null) pb.environment().put("SystemRoot", systemRoot);
            pb.directory(workDir.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
            process = pb.start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                kill(process);
                return new CompileResult(false, "COMPILATION_TIMEOUT: компиляция превысила 20 секунд", out);
            }
            String output;
            try (var input = Files.newInputStream(log)) {
                output = new String(input.readNBytes(1_000_000), StandardCharsets.UTF_8);
            }
            return new CompileResult(process.exitValue() == 0, output.trim(), out);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Компиляция прервана", e);
        } finally {
            if (process != null && process.isAlive()) kill(process);
            if (acquired) SLOTS.release();
        }
    }

    private static String quote(Path path) {
        return "\"" + path.toString().replace('\\', '/').replace("\"", "\\\"") + "\"";
    }

    private static void kill(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        try { process.waitFor(2, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

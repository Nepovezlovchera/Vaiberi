package com.axenix.blueprints.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Standalone worker: no Spring API, LLM configuration, cache or host mounts. */
public final class SandboxWorker {
    public record Job(Map<String, byte[]> classes, String mainClass, String stdin) {}
    private static final int MAX_BODY = 32_000_000;

    public static void main(String[] args) throws Exception {
        ObjectMapper json = new ObjectMapper();
        ProcessRunner runner = new ProcessRunner(3000, "128m");
        HttpServer http = HttpServer.create(new InetSocketAddress(8090), 4);
        http.setExecutor(new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(4), new ThreadPoolExecutor.AbortPolicy()));
        http.createContext("/health", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        http.createContext("/run", exchange -> {
            Path temp = null;
            try {
                if (!"POST".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
                if (body.length > MAX_BODY) { exchange.sendResponseHeaders(413, -1); return; }
                Job job = json.readValue(body, Job.class);
                if (job.classes() == null || job.classes().size() > 4096 || job.mainClass() == null
                        || !job.mainClass().matches("[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}.$]*")
                        || (job.stdin() != null && job.stdin().length() > 1_000_000))
                    throw new IllegalArgumentException("Invalid job");
                temp = Files.createTempDirectory("bp-worker-");
                Path classes = Files.createDirectory(temp.resolve("classes"));
                long bytes = 0;
                for (var file : job.classes().entrySet()) {
                    Path target = classes.resolve(file.getKey()).normalize();
                    if (!target.startsWith(classes) || !file.getKey().endsWith(".class") || file.getValue() == null)
                        throw new IllegalArgumentException("Invalid class path");
                    bytes += file.getValue().length;
                    if (bytes > 20_000_000) throw new IllegalArgumentException("Classes too large");
                    Files.createDirectories(target.getParent());
                    Files.write(target, file.getValue());
                }
                byte[] response = json.writeValueAsBytes(runner.run(classes, job.mainClass(), job.stdin()));
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } catch (Exception e) {
                if (exchange.getResponseCode() == -1) exchange.sendResponseHeaders(400, -1);
            } finally {
                RunServiceImpl.deleteQuietly(temp);
                exchange.close();
            }
        });
        http.start();
    }
}

package com.axenix.blueprints.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;

/** Sends only compiled classes and stdin to the isolated runner container. */
public final class RemoteProcessRunner extends ProcessRunner {
    private final URI endpoint;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public RemoteProcessRunner(String url) {
        super(3000, "128m");
        endpoint = URI.create(url + "/run");
    }

    @Override public RunResult run(Path classesDir, String mainClass, String stdin) throws IOException, InterruptedException {
        var classes = new LinkedHashMap<String, byte[]>();
        long total = 0;
        try (var paths = Files.walk(classesDir)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".class")).toList()) {
                total += Files.size(path);
                if (total > 20_000_000 || classes.size() >= 4096) throw new IOException("Compiled project exceeds runner limits");
                classes.put(classesDir.relativize(path).toString().replace('\\', '/'), Files.readAllBytes(path));
            }
        }
        byte[] body = json.writeValueAsBytes(new SandboxWorker.Job(classes, mainClass, stdin));
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        HttpResponse<java.io.InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var stream = response.body()) {
            if (response.statusCode() != 200) throw new IOException("Runner rejected job: HTTP " + response.statusCode());
            byte[] result = stream.readNBytes(4_000_001);
            if (result.length > 4_000_000) throw new IOException("Runner response exceeds limit");
            return json.readValue(result, RunResult.class);
        }
    }
}

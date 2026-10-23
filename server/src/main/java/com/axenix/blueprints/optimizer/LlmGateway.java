package com.axenix.blueprints.optimizer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Доступ к LLM с файловым кэшем (ответы для демо-монолита можно закэшировать заранее: сеть на питче может подвести).
 * Поддерживает любой OpenAI-совместимый /chat/completions. Настройка через переменные окружения:
 * LLM_API_URL (по умолчанию OpenAI), LLM_API_KEY, LLM_MODEL. Без ключа работает только кэш.
 */
public class LlmGateway {

    public record Answer(String text, boolean cached) {}

    private final Path cacheDir;
    private final String url;
    private final String key;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public LlmGateway(Path cacheDir, String url, String key, String model) {
        this.cacheDir = cacheDir;
        this.url = url == null || url.isBlank() ? "https://api.openai.com/v1/chat/completions" : url;
        this.key = key;
        this.model = model == null || model.isBlank() ? "gpt-4o-mini" : model;
    }

    public boolean configured() {
        return key != null && !key.isBlank();
    }

    public Optional<Answer> ask(String prompt) throws IOException, InterruptedException {
        Path file = cacheDir.resolve(sha256(model + "\n" + prompt) + ".txt");
        if (Files.exists(file)) return Optional.of(new Answer(Files.readString(file, StandardCharsets.UTF_8), true));
        if (!configured()) return Optional.empty();

        String body = "{\"model\":" + quote(model) + ",\"temperature\":0,\"messages\":[{\"role\":\"user\",\"content\":"
                + quote(prompt) + "}]}";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + key)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) throw new IOException("LLM вернул HTTP " + resp.statusCode());
        String text = extractContent(resp.body());
        if (text == null) throw new IOException("Не удалось прочитать ответ LLM");
        Files.createDirectories(cacheDir);
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return Optional.of(new Answer(text, false));
    }

    // ---- минимальная работа с JSON без внешних библиотек ----

    static String quote(String s) {
        return com.axenix.blueprints.model.Json.quote(s);
    }

    private static final Pattern CONTENT = Pattern.compile("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    static String extractContent(String json) {
        Matcher m = CONTENT.matcher(json);
        if (!m.find()) return null;
        String raw = m.group(1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\\' || i + 1 >= raw.length()) {
                sb.append(c);
                continue;
            }
            char n = raw.charAt(++i);
            switch (n) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'u' -> {
                    sb.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> sb.append(n); // \" \\ \/
            }
        }
        return sb.toString();
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

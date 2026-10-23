package com.axenix.blueprints.sandbox;

import com.axenix.blueprints.model.Dto.TestCaseResult;
import com.axenix.blueprints.model.Dto.VerificationReport;
import com.axenix.blueprints.model.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Сохраняет и отдаёт verification_report.json (артефакт для сдачи по слайду 11). */
public class ReportStore {

    private final Path file; // null → не сохранять

    public ReportStore(Path file) {
        this.file = file;
    }

    public void save(VerificationReport r) {
        if (file == null) return;
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(file, toJson(r), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // отчёт — удобство, а не часть ответа: сбой записи не должен ронять проверку
        }
    }

    public Optional<String> load() {
        try {
            return file != null && Files.exists(file) ? Optional.of(Files.readString(file, StandardCharsets.UTF_8)) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public static String toJson(VerificationReport r) {
        StringBuilder sb = new StringBuilder("{\n");
        sb.append("  \"allPassed\": ").append(r.allPassed()).append(",\n");
        sb.append("  \"behaviorMatched\": ").append(r.behaviorMatched()).append(",\n");
        sb.append("  \"originalTimeMs\": ").append(r.originalTimeMs()).append(",\n");
        sb.append("  \"optimizedTimeMs\": ").append(r.optimizedTimeMs()).append(",\n");
        sb.append("  \"speedup\": ").append(Json.quote(r.speedup())).append(",\n");
        sb.append("  \"complexityBefore\": ").append(Json.quote(r.complexityBefore())).append(",\n");
        sb.append("  \"complexityAfter\": ").append(Json.quote(r.complexityAfter())).append(",\n");
        sb.append("  \"passed\": ").append(r.passed()).append(",\n");
        sb.append("  \"total\": ").append(r.total()).append(",\n");
        sb.append("  \"testResults\": [\n");
        for (int i = 0; i < r.testResults().size(); i++) {
            TestCaseResult t = r.testResults().get(i);
            sb.append("    { \"name\": ").append(Json.quote(t.name())).append(", \"status\": ").append(Json.quote(t.status()))
                    .append(", \"durationMs\": ").append(t.durationMs());
            if (t.errorDetails() != null) sb.append(", \"errorDetails\": ").append(Json.quote(t.errorDetails()));
            sb.append(" }").append(i + 1 < r.testResults().size() ? "," : "").append('\n');
        }
        sb.append("  ],\n  \"logs\": [\n");
        for (int i = 0; i < r.logs().size(); i++) {
            sb.append("    ").append(Json.quote(r.logs().get(i))).append(i + 1 < r.logs().size() ? "," : "").append('\n');
        }
        sb.append("  ],\n");
        sb.append("  \"timestamp\": ").append(Json.quote(r.timestamp())).append("\n}\n");
        return sb.toString();
    }
}

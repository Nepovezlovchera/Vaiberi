package com.axenix.blueprints.controller;

import com.axenix.blueprints.model.Dto;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Request limits are enforced before parsing, compiling, or running user supplied code. */
final class RequestLimits {
    static final int MAX_FILES = 64;
    static final int MAX_FILE_BYTES = 1_000_000;
    static final int MAX_TOTAL_BYTES = 10_000_000;
    static final int MAX_TESTS = 100;
    static final int MAX_INPUT_BYTES = 1_000_000;
    private static final int MAX_PATH_LENGTH = 240;

    private RequestLimits() {}

    static void files(List<Dto.ProjectFile> files, String field) {
        if (files == null || files.isEmpty()) throw new IllegalArgumentException(field + " не должен быть пустым");
        if (files.size() > MAX_FILES) throw new IllegalArgumentException(field + ": слишком много файлов");
        long total = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Dto.ProjectFile file : files) {
            if (file == null || file.path() == null || file.source() == null)
                throw new IllegalArgumentException(field + ": путь и исходный код обязательны");
            String path = file.path().replace('\\', '/');
            if (path.isBlank() || path.length() > MAX_PATH_LENGTH || path.chars().anyMatch(Character::isISOControl)
                    || path.contains("\"") || path.contains(":")
                    || path.startsWith("/") || path.matches("^[A-Za-z]:.*") || path.contains("../")
                    || path.equals("..") || path.endsWith("/..") || !path.endsWith(".java")) {
                throw new IllegalArgumentException("Недопустимый путь файла: " + file.path());
            }
            path = java.nio.file.Path.of(path).normalize().toString();
            if (!seen.add(path)) throw new IllegalArgumentException("Повторный путь файла: " + path);
            int bytes = file.source().getBytes(StandardCharsets.UTF_8).length;
            if (bytes > MAX_FILE_BYTES) throw new IllegalArgumentException("Файл слишком большой: " + file.path());
            total += bytes;
            if (total > MAX_TOTAL_BYTES) throw new IllegalArgumentException(field + ": общий размер файлов слишком большой");
        }
    }

    static void graph(Dto.GraphRequest request) { files(request == null ? null : request.files(), "files"); }
    static void filesRequest(Dto.FilesRequest request) { files(request == null ? null : request.files(), "files"); }
    static void mutate(Dto.MutateRequest request) { files(request == null ? null : request.files(), "files"); }
    static void optimize(Dto.OptimizeRequest request) { files(request == null ? null : request.files(), "files"); }
    static void decompose(Dto.DecomposeRequest request) { files(request == null ? null : request.files(), "files"); }
    static void run(Dto.RunRequest request) {
        files(request == null ? null : request.files(), "files");
        if (request.stdin() != null && request.stdin().getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES)
            throw new IllegalArgumentException("stdin слишком большой");
    }
    static void verify(Dto.VerifyRequest request) {
        if (request == null) throw new IllegalArgumentException("Запрос обязателен");
        files(request.original(), "original");
        files(request.modified(), "modified");
        List<Dto.TestCase> tests = request.tests();
        if (tests != null && tests.size() > MAX_TESTS) throw new IllegalArgumentException("Слишком много тестов");
        if (tests != null) for (Dto.TestCase test : tests) {
            if (test == null || test.name() == null || test.name().isBlank())
                throw new IllegalArgumentException("Имя теста обязательно");
            if (test.name().length() > 200) throw new IllegalArgumentException("Имя теста слишком длинное");
            if (test.stdin() != null && test.stdin().getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES)
                throw new IllegalArgumentException("stdin теста слишком большой: " + test.name());
            if (test.expectedStdout() != null && test.expectedStdout().getBytes(StandardCharsets.UTF_8).length > MAX_OUTPUT_BYTES)
                throw new IllegalArgumentException("Ожидаемый вывод слишком большой: " + test.name());
        }
    }

    private static final int MAX_OUTPUT_BYTES = 1_000_000;
}

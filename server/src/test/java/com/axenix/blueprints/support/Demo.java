package com.axenix.blueprints.support;

import com.axenix.blueprints.model.Dto.CompareMode;
import com.axenix.blueprints.model.Dto.ProjectFile;
import com.axenix.blueprints.model.Dto.TestCase;
import com.axenix.blueprints.model.Dto.VerifyRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Загрузка демо-монолита и входных данных из test resources. */
public final class Demo {
    private Demo() {}

    public static final List<String> TESTS = List.of("basic", "empty", "single", "duplicates_and_invalid");

    public static String read(String resource) {
        try (InputStream in = Demo.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Нет ресурса " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static List<ProjectFile> project() {
        return List.of(new ProjectFile("Main.java", read("/demo/Main.java")));
    }

    public static List<TestCase> testCases() {
        List<TestCase> out = new ArrayList<>();
        for (String name : TESTS) out.add(new TestCase(name, read("/demo/tests/" + name + ".in"), null));
        return out;
    }

    public static VerifyRequest verify(List<ProjectFile> modified) {
        return new VerifyRequest(project(), modified, "Main", testCases(), CompareMode.EXACT);
    }
}

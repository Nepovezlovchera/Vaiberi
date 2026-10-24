package com.axenix.blueprints.controller;

import com.axenix.blueprints.model.Dto;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RequestLimitsTest {
    @Test void rejectsMissingAndTraversingFiles() {
        assertThrows(IllegalArgumentException.class, () -> RequestLimits.files(null, "files"));
        assertThrows(IllegalArgumentException.class, () -> RequestLimits.files(java.util.Arrays.asList((Dto.ProjectFile) null), "files"));
        for (String path : List.of("../Main.java", "a/../../Main.java", "C:\\Main.java", "/Main.java")) {
            assertThrows(IllegalArgumentException.class, () -> RequestLimits.files(List.of(new Dto.ProjectFile(path, "class Main {}")), "files"));
        }
    }
    @Test void rejectsOversizedSourceAndTestInput() {
        assertThrows(IllegalArgumentException.class, () -> RequestLimits.files(List.of(new Dto.ProjectFile("Main.java", "x".repeat(1_000_001))), "files"));
        assertThrows(IllegalArgumentException.class, () -> RequestLimits.run(new Dto.RunRequest(
                List.of(new Dto.ProjectFile("Main.java", "class Main {}")), null, "x".repeat(1_000_001))));
    }
}

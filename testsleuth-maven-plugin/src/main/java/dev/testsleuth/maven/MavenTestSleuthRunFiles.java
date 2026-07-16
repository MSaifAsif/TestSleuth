package dev.testsleuth.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

final class MavenTestSleuthRunFiles {
    private static final List<String> PER_RUN_FILE_NAMES = List.of(
            "events.json",
            "findings.json",
            "junit-events.json",
            "junit4-events.json",
            "runtime-wait-events.json"
    );

    void reset(Path outputDirectory) {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        for (String fileName : PER_RUN_FILE_NAMES) {
            deleteIfExists(outputDirectory.resolve(fileName));
        }
    }

    private static void deleteIfExists(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to reset TestSleuth run file " + file, e);
        }
    }
}

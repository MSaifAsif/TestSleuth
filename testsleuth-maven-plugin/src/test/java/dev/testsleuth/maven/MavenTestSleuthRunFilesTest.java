package dev.testsleuth.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MavenTestSleuthRunFilesTest {
    @TempDir
    private Path tempDir;

    @Test
    void resetsPerRunOutputWithoutDeletingStableFiles() throws Exception {
        Path output = tempDir.resolve("testsleuth");
        Files.createDirectories(output);
        Files.writeString(output.resolve("events.json"), "stale");
        Files.writeString(output.resolve("findings.json"), "stale");
        Files.writeString(output.resolve("junit-events.json"), "stale");
        Files.writeString(output.resolve("junit4-events.json"), "stale");
        Files.writeString(output.resolve("runtime-wait-events.json"), "stale");
        Files.writeString(output.resolve(MavenBuildTiming.TIMING_FILE_NAME), "keep");

        new MavenTestSleuthRunFiles().reset(output);

        assertFalse(Files.exists(output.resolve("events.json")));
        assertFalse(Files.exists(output.resolve("findings.json")));
        assertFalse(Files.exists(output.resolve("junit-events.json")));
        assertFalse(Files.exists(output.resolve("junit4-events.json")));
        assertFalse(Files.exists(output.resolve("runtime-wait-events.json")));
        assertTrue(Files.exists(output.resolve(MavenBuildTiming.TIMING_FILE_NAME)));
    }
}

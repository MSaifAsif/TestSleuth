package dev.testsleuth.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class MavenBuildTimingTest {
    @TempDir
    private Path tempDir;

    @Test
    void writesAndLoadsRunTiming() {
        MavenBuildTiming timing = new MavenBuildTiming();
        timing.start(tempDir);

        assertTrue(Files.isRegularFile(tempDir.resolve(MavenBuildTiming.TIMING_FILE_NAME)));
        assertTrue(timing.load(tempDir).isPresent());
        assertTrue(timing.load(tempDir).orElseThrow().elapsedSinceStart().toMillis() >= 0);
    }
}

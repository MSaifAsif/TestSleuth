package dev.testsleuth.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MavenJfrRecordingTest {
    @TempDir
    Path tempDir;

    @Test
    void preparesARecordingDirectoryAndSummarizesJfrFiles() throws Exception {
        MavenJfrRecording recording = new MavenJfrRecording();
        recording.reset(tempDir);
        Path directory = recording.recordingsDirectory(tempDir);
        Path first = directory.resolve("surefire-1.jfr");
        Path second = directory.resolve("failsafe-1.jfr");
        Files.write(first, new byte[]{1, 2, 3});
        Files.write(second, new byte[]{4, 5});

        MavenJfrRecording.Summary summary = recording.summarize(tempDir, true);

        assertEquals(2, summary.recordingCount());
        assertEquals(5, summary.recordingBytes());
        assertEquals(directory, summary.recordingsDirectory());
        assertTrue(summary.consoleLine().contains("2 files, 5 bytes"));
        assertEquals("JFR capture retained 2 raw recordings (5 bytes). ",
                summary.reportSentence());
    }

    @Test
    void suppressesJfrStartupMessagesThatCorruptSurefireForkChannels() {
        String argument = new MavenJfrRecording().recordingArgumentForProcess(tempDir);

        assertTrue(argument.contains("-XX:StartFlightRecording="));
        assertTrue(argument.endsWith(" -Xlog:jfr+startup=off"));
    }
}

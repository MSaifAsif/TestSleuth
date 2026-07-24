package dev.testsleuth.maven;

import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MavenJfrRecordingReaderTest {
    @TempDir
    Path tempDir;

    @Test
    void parsesValidRecordingsAndSkipsUnreadableFiles() throws Exception {
        Path recording = tempDir.resolve("valid.jfr");
        try (Recording activeRecording = new Recording()) {
            activeRecording.enable(SampleJfrEvent.class).withThreshold(Duration.ZERO);
            activeRecording.start();
            new SampleJfrEvent().commit();
            activeRecording.stop();
            activeRecording.dump(recording);
        }
        java.nio.file.Files.writeString(tempDir.resolve("broken.jfr"), "not a recording");

        MavenJfrRecordingReader.Summary summary = new MavenJfrRecordingReader().summarize(tempDir, true);

        assertEquals(2, summary.recordingCount());
        assertEquals(1, summary.parsedRecordings());
        assertEquals(1, summary.unreadableRecordings());
        assertTrue(summary.totalEvents() > 0);
        assertTrue(summary.consoleLine().contains("1/2 recordings parsed"));
        assertTrue(summary.reportSentence().contains("Parsed 1 of 2 JFR recordings"));
    }

    @Name("dev.testsleuth.test.SampleJfrEvent")
    static final class SampleJfrEvent extends Event {
    }
}

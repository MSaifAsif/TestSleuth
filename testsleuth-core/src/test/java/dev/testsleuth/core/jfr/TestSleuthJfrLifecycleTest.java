package dev.testsleuth.core.jfr;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TestSleuthJfrLifecycleTest {
    @TempDir
    Path tempDir;

    @Test
    void recordsCanonicalTestLifecycleDataIntoJfr() throws Exception {
        Path recordingFile = tempDir.resolve("lifecycle.jfr");
        try (Recording recording = new Recording()) {
            recording.enable(TestSleuthJfrLifecycle.TestLifecycleEvent.class);
            recording.start();
            TestSleuthJfrLifecycle.Span span = TestSleuthJfrLifecycle.beginTest(
                    "example.SampleTest.slowTest", "slowTest()", "junit5"
            );
            span.finish("passed");
            recording.stop();
            recording.dump(recordingFile);
        }

        List<jdk.jfr.consumer.RecordedEvent> events = RecordingFile.readAllEvents(recordingFile);
        jdk.jfr.consumer.RecordedEvent event = events.stream()
                .filter(candidate -> candidate.getEventType().getName().equals("dev.testsleuth.TestLifecycle"))
                .findFirst()
                .orElseThrow();

        assertEquals("test", event.getString("phase"));
        assertEquals("example.SampleTest.slowTest", event.getString("testIdentity"));
        assertEquals("junit5", event.getString("framework"));
        assertEquals("passed", event.getString("outcome"));
    }

    @Test
    void recordsNamedSetupPhaseIntoJfr() throws Exception {
        Path recordingFile = tempDir.resolve("setup.jfr");
        try (Recording recording = new Recording()) {
            recording.enable(TestSleuthJfrLifecycle.TestLifecycleEvent.class);
            recording.start();
            TestSleuthJfrLifecycle.Span span = TestSleuthJfrLifecycle.beginPhase(
                    "before-each", "example.SampleTest.slowTest", "slowTest()", "junit5"
            );
            span.finish("completed");
            recording.stop();
            recording.dump(recordingFile);
        }

        jdk.jfr.consumer.RecordedEvent event = RecordingFile.readAllEvents(recordingFile).stream()
                .filter(candidate -> candidate.getEventType().getName().equals("dev.testsleuth.TestLifecycle"))
                .findFirst()
                .orElseThrow();

        assertEquals("before-each", event.getString("phase"));
        assertEquals("completed", event.getString("outcome"));
    }
}

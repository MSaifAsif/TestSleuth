package dev.testsleuth.junit4;

import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.TestSleuthEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.runner.Description;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JUnit4EventCollectorTest {
    @TempDir
    private Path tempDir;

    @Test
    void recordsStartedAndFinishedTestEvents() {
        JUnit4EventCollector collector = new JUnit4EventCollector();
        Description description = Description.createTestDescription(ExampleTest.class, "passes");

        collector.recordStarted(description);
        collector.recordFinished(description);

        List<TestSleuthEvent> events = collector.events();
        assertEquals(2, events.size());
        assertEquals(EventKind.TEST_STARTED, events.get(0).kind());
        assertEquals(EventKind.TEST_FINISHED, events.get(1).kind());
        assertEquals("dev.testsleuth.junit4.JUnit4EventCollectorTest$ExampleTest.passes",
                events.get(1).attributes().get("testIdentity"));
        assertEquals("junit4-listener", events.get(1).attributes().get("collector"));
        assertEquals("passed", events.get(1).attributes().get("status"));
        assertTrue(Long.parseLong(events.get(1).attributes().get("durationMillis")) >= 0);
    }

    @Test
    void writesEventsToConfiguredFile() throws Exception {
        JUnit4EventCollector collector = new JUnit4EventCollector();
        Description description = Description.createTestDescription(ExampleTest.class, "passes");
        Path eventsFile = tempDir.resolve("events/junit4-events.json");

        collector.recordStarted(description);
        collector.recordFinished(description);
        collector.writeTo(eventsFile, message -> {
            throw new AssertionError(message);
        });

        String json = Files.readString(eventsFile);
        assertTrue(json.contains("\"collector\":\"junit4-listener\""));
        assertTrue(json.contains("\"kind\":\"TEST_FINISHED\""));
    }

    static final class ExampleTest {
    }
}

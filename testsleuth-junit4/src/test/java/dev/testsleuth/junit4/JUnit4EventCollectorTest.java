package dev.testsleuth.junit4;

import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.TestSleuthEvent;
import org.junit.AssumptionViolatedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;

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

    @Test
    void recordsFailedTestStatus() {
        JUnit4EventCollector collector = new JUnit4EventCollector();
        Description description = Description.createTestDescription(ExampleTest.class, "fails");

        collector.recordStarted(description);
        collector.recordFailure(new Failure(description, new AssertionError("boom")));
        collector.recordFinished(description);

        TestSleuthEvent finished = collector.events().get(1);
        assertEquals("failed", finished.attributes().get("status"));
        assertEquals(AssertionError.class.getName(), finished.attributes().get("throwableType"));
    }

    @Test
    void recordsAssumptionFailureAsSkipped() {
        JUnit4EventCollector collector = new JUnit4EventCollector();
        Description description = Description.createTestDescription(ExampleTest.class, "skips");

        collector.recordStarted(description);
        collector.recordAssumptionFailure(new Failure(description, new AssumptionViolatedException("skip")));
        collector.recordFinished(description);

        TestSleuthEvent finished = collector.events().get(1);
        assertEquals("skipped", finished.attributes().get("status"));
        assertEquals(AssumptionViolatedException.class.getName(), finished.attributes().get("throwableType"));
    }

    @Test
    void recordsIgnoredTestAsSkippedWithoutStartEvent() {
        JUnit4EventCollector collector = new JUnit4EventCollector();
        Description description = Description.createTestDescription(ExampleTest.class, "ignored");

        collector.recordIgnored(description);

        List<TestSleuthEvent> events = collector.events();
        assertEquals(1, events.size());
        assertEquals(EventKind.TEST_FINISHED, events.get(0).kind());
        assertEquals("skipped", events.get(0).attributes().get("status"));
        assertEquals("0", events.get(0).attributes().get("durationMillis"));
    }

    static final class ExampleTest {
    }
}

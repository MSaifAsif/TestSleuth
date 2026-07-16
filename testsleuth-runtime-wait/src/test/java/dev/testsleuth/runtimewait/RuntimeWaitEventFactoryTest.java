package dev.testsleuth.runtimewait;

import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.EventId;
import dev.testsleuth.core.event.EventJsonReader;
import dev.testsleuth.core.event.EventJsonWriter;
import dev.testsleuth.core.event.RuntimeWaitEventAttributes;
import dev.testsleuth.core.event.Subject;
import dev.testsleuth.core.event.SubjectType;
import dev.testsleuth.core.event.TestSleuthEvent;
import dev.testsleuth.core.event.TestSleuthRunContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RuntimeWaitEventFactoryTest {
    @TempDir
    Path tempDir;

    @Test
    void createsNormalizedWaitFinishedEvent() {
        RuntimeWaitEventFactory factory = new RuntimeWaitEventFactory(new TestSleuthRunContext(
                "build-1",
                "module-a",
                "dev.testsleuth",
                "sample",
                "1.0",
                "/repo/sample",
                "123",
                "1"
        ));
        RuntimeWaitObservation observation = new RuntimeWaitObservation(
                RuntimeWaitOperations.THREAD_SLEEP,
                Duration.ofMillis(125),
                Optional.of(Duration.ofMillis(250)),
                RuntimeWaitEventAttributes.WaitOutcome.COMPLETED,
                Thread.currentThread(),
                Instant.parse("2026-07-16T09:00:00Z"),
                42,
                17,
                Optional.of("SlowSampleTest.java:31"),
                Optional.of("dev.example.SlowSampleTest.slow")
        );

        TestSleuthEvent event = factory.finishedEvent(observation);

        assertEquals(EventKind.WAIT_FINISHED, event.kind());
        assertEquals(SubjectType.WAIT, event.subject().type());
        assertEquals(RuntimeWaitOperations.THREAD_SLEEP, event.subject().identifier());
        assertEquals("runtime-wait-collector", event.attributes().get("collector"));
        assertEquals("125", event.attributes().get(RuntimeWaitEventAttributes.OBSERVED_DURATION_MILLIS));
        assertEquals("250", event.attributes().get(RuntimeWaitEventAttributes.CONFIGURED_TIMEOUT_MILLIS));
        assertEquals("completed", event.attributes().get(RuntimeWaitEventAttributes.OUTCOME));
        assertEquals("17", event.attributes().get(RuntimeWaitEventAttributes.COLLECTOR_OVERHEAD_NANOS));
        assertEquals("SlowSampleTest.java:31", event.attributes().get(RuntimeWaitEventAttributes.SOURCE_LOCATION));
        assertEquals("dev.example.SlowSampleTest.slow", event.attributes().get(RuntimeWaitEventAttributes.STACK_TOP));
        assertEquals("build-1", event.attributes().get("buildRunId"));
        assertEquals("module-a", event.attributes().get("moduleId"));
        assertEquals("sample", event.attributes().get("projectArtifactId"));
    }

    @Test
    void rejectsNegativeCollectorOverhead() {
        assertThrows(IllegalArgumentException.class, () -> new RuntimeWaitObservation(
                RuntimeWaitOperations.OBJECT_WAIT,
                Duration.ofMillis(1),
                Optional.empty(),
                RuntimeWaitEventAttributes.WaitOutcome.TIMED_OUT,
                Thread.currentThread(),
                Instant.EPOCH,
                1,
                -1,
                Optional.empty(),
                Optional.empty()
        ));
    }

    @Test
    void capturesThreadSleepAndLockSupportWaits() throws InterruptedException {
        RuntimeWaitCollector collector = new RuntimeWaitCollector(runContext());

        collector.sleep(Duration.ZERO);
        collector.parkNanos(Duration.ZERO);
        collector.parkUntil(Instant.EPOCH);

        assertEquals(3, collector.events().size());
        assertEquals(RuntimeWaitOperations.THREAD_SLEEP, collector.events().get(0).subject().identifier());
        assertEquals(RuntimeWaitOperations.LOCK_SUPPORT_PARK_NANOS, collector.events().get(1).subject().identifier());
        assertEquals(RuntimeWaitOperations.LOCK_SUPPORT_PARK_UNTIL, collector.events().get(2).subject().identifier());
        assertFalse(collector.events().get(0).attributes()
                .get(RuntimeWaitEventAttributes.COLLECTOR_OVERHEAD_NANOS)
                .isBlank());
    }

    @Test
    void capturesObjectWait() throws InterruptedException {
        RuntimeWaitCollector collector = new RuntimeWaitCollector(runContext());
        Object monitor = new Object();

        synchronized (monitor) {
            collector.waitOn(monitor, Duration.ofMillis(1));
        }

        assertEquals(1, collector.events().size());
        TestSleuthEvent event = collector.events().get(0);
        assertEquals(RuntimeWaitOperations.OBJECT_WAIT, event.subject().identifier());
        assertEquals("timed-out", event.attributes().get(RuntimeWaitEventAttributes.OUTCOME));
    }

    @Test
    void writesAndMergesRuntimeWaitEvents() throws Exception {
        RuntimeWaitCollector collector = new RuntimeWaitCollector(runContext());
        collector.parkNanos(Duration.ZERO);
        Path eventsFile = tempDir.resolve("runtime-events.json");
        TestSleuthEvent existing = new TestSleuthEvent(
                new EventId("existing"),
                Optional.empty(),
                EventKind.TEST_FINISHED,
                new Subject(SubjectType.TEST_METHOD, "test"),
                Instant.EPOCH,
                1,
                Map.of("collector", "test")
        );
        Files.writeString(eventsFile, new EventJsonWriter().write(List.of(existing)), StandardCharsets.UTF_8);

        collector.writeTo(eventsFile, failure -> {
            throw new AssertionError(failure);
        });

        List<TestSleuthEvent> events = new EventJsonReader().read(Files.readString(eventsFile, StandardCharsets.UTF_8));
        assertEquals(2, events.size());
        assertEquals(EventKind.TEST_FINISHED, events.get(0).kind());
        assertEquals(EventKind.WAIT_FINISHED, events.get(1).kind());
    }

    private static TestSleuthRunContext runContext() {
        return new TestSleuthRunContext(
                "build-1",
                "module-a",
                "dev.testsleuth",
                "sample",
                "1.0",
                "/repo/sample",
                "123",
                "1"
        );
    }
}

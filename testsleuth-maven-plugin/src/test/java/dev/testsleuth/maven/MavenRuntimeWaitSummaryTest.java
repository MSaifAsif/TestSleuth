package dev.testsleuth.maven;

import dev.testsleuth.core.event.EventId;
import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.RuntimeWaitEventAttributes;
import dev.testsleuth.core.event.Subject;
import dev.testsleuth.core.event.SubjectType;
import dev.testsleuth.core.event.TestSleuthEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MavenRuntimeWaitSummaryTest {
    @Test
    void aggregatesRuntimeWaitEvents() {
        MavenRuntimeWaitSummary summary = MavenRuntimeWaitSummary.from(List.of(
                runtimeWaitEvent("wait-1", 100, 10),
                runtimeWaitEvent("wait-2", 250, 20),
                nonRuntimeWaitEvent()
        ));

        assertTrue(summary.hasEvents());
        assertEquals(2, summary.eventCount());
        assertEquals(350, summary.observedWaitTime().toMillis());
        assertEquals(30, summary.collectorOverhead().toNanos());
    }

    @Test
    void reportsNoEventsForEmptyInput() {
        MavenRuntimeWaitSummary summary = MavenRuntimeWaitSummary.from(List.of());

        assertFalse(summary.hasEvents());
        assertEquals(0, summary.eventCount());
        assertEquals(0, summary.observedWaitTime().toMillis());
        assertEquals(0, summary.collectorOverhead().toNanos());
    }

    private static TestSleuthEvent runtimeWaitEvent(String id, long durationMillis, long overheadNanos) {
        return new TestSleuthEvent(
                new EventId(id),
                Optional.empty(),
                EventKind.WAIT_FINISHED,
                new Subject(SubjectType.WAIT, "Thread.sleep"),
                Instant.EPOCH,
                0,
                Map.of(
                        "collector", RuntimeWaitEventAttributes.COLLECTOR,
                        RuntimeWaitEventAttributes.OBSERVED_DURATION_MILLIS, Long.toString(durationMillis),
                        RuntimeWaitEventAttributes.COLLECTOR_OVERHEAD_NANOS, Long.toString(overheadNanos)
                )
        );
    }

    private static TestSleuthEvent nonRuntimeWaitEvent() {
        return new TestSleuthEvent(
                new EventId("test"),
                Optional.empty(),
                EventKind.TEST_FINISHED,
                new Subject(SubjectType.TEST_METHOD, "ExampleTest.test"),
                Instant.EPOCH,
                0,
                Map.of("collector", "maven-test-report", "durationMillis", "1000")
        );
    }
}

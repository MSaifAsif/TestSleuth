package dev.testsleuth.maven;

import dev.testsleuth.core.event.EventId;
import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.RuntimeWaitEventAttributes;
import dev.testsleuth.core.event.Subject;
import dev.testsleuth.core.event.SubjectType;
import dev.testsleuth.core.event.TestSleuthEvent;
import dev.testsleuth.core.finding.Finding;
import dev.testsleuth.core.finding.FindingCategory;
import dev.testsleuth.core.finding.FindingSeverity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MavenRuntimeWaitFindingsTest {
    @Test
    void returnsNoFindingsWhenRuntimeWaitsAreDisabled() {
        TestSleuthMavenConfig config = TestSleuthMavenConfig.from(
                true, "summary", 100, 1_000, 10, true, false, 250,
                false, 100, false, false, false
        );

        List<Finding> findings = new MavenRuntimeWaitFindings(config).detect(List.of(waitEvent("runtime-wait-1", 750)));

        assertEquals(0, findings.size());
    }

    @Test
    void createsFindingsForRuntimeWaitEventsAboveThreshold() {
        TestSleuthMavenConfig config = TestSleuthMavenConfig.from(
                true, "summary", 500, 1_000, 10, true, false, 250,
                false, 100, false, true, false
        );

        List<Finding> findings = new MavenRuntimeWaitFindings(config).detect(List.of(
                waitEvent("short", 100),
                waitEvent("medium", 750),
                waitEvent("slow", 1_250)
        ));

        assertEquals(2, findings.size());
        Finding slowest = findings.get(0);
        assertEquals("Runtime wait observed: LockSupport.parkNanos", slowest.title());
        assertEquals(FindingCategory.WAITING, slowest.category());
        assertEquals(FindingSeverity.HIGH, slowest.severity());
        assertEquals(1_250, slowest.observedCost().toMillis());
        assertEquals("LockSupport.parkNanos observed 1250 ms at runtime.", slowest.evidence().get(0));
        assertEquals("Configured timeout: 1250 ms.", slowest.evidence().get(1));
        assertEquals("Outcome: timed-out.", slowest.evidence().get(2));
        assertEquals("Detector: runtime-waits.", slowest.evidence().get(5));
    }

    @Test
    void respectsMaxFindings() {
        TestSleuthMavenConfig config = TestSleuthMavenConfig.from(
                true, "summary", 1, 1_000, 1, true, false, 1,
                false, 100, false, true, false
        );

        List<Finding> findings = new MavenRuntimeWaitFindings(config).detect(List.of(
                waitEvent("slowest", 300),
                waitEvent("slower", 200)
        ));

        assertEquals(1, findings.size());
        assertEquals(300, findings.get(0).observedCost().toMillis());
    }

    private static TestSleuthEvent waitEvent(String id, long durationMillis) {
        return new TestSleuthEvent(
                new EventId(id),
                Optional.empty(),
                EventKind.WAIT_FINISHED,
                new Subject(SubjectType.WAIT, "LockSupport.parkNanos"),
                Instant.EPOCH,
                0,
                Map.ofEntries(
                        Map.entry("collector", RuntimeWaitEventAttributes.COLLECTOR),
                        Map.entry(RuntimeWaitEventAttributes.OPERATION, "LockSupport.parkNanos"),
                        Map.entry(RuntimeWaitEventAttributes.OBSERVED_DURATION_MILLIS, Long.toString(durationMillis)),
                        Map.entry(RuntimeWaitEventAttributes.CONFIGURED_TIMEOUT_MILLIS, Long.toString(durationMillis)),
                        Map.entry(RuntimeWaitEventAttributes.OUTCOME, "timed-out"),
                        Map.entry(RuntimeWaitEventAttributes.THREAD_NAME, "main"),
                        Map.entry(RuntimeWaitEventAttributes.COLLECTOR_OVERHEAD_NANOS, "42"),
                        Map.entry("moduleId", "dev.testsleuth:sample"),
                        Map.entry("buildRunId", "run-1"),
                        Map.entry("processId", "123"),
                        Map.entry("forkNumber", "1")
                )
        );
    }
}

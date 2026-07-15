package dev.testsleuth.maven;

import dev.testsleuth.core.event.EventId;
import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.Subject;
import dev.testsleuth.core.event.SubjectType;
import dev.testsleuth.core.event.TestSleuthEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MavenTimingSummaryTest {
    @Test
    void reconcilesLifecycleWindowWithObservedTestDurations() {
        MavenTimingSummary summary = MavenTimingSummary.from(
                Optional.of(Duration.ofMillis(2_000)),
                List.of(
                        event("maven-1", "maven-test-report", 700),
                        event("maven-2", "maven-test-report", 300),
                        event("junit-1", "junit5-listener", 650),
                        event("junit-2", "junit5-listener", 250),
                        event("setup-1", EventKind.SETUP_FINISHED, "junit5-listener", 40),
                        event("teardown-1", EventKind.TEARDOWN_FINISHED, "junit5-listener", 25)
                )
        );

        assertEquals(1_000, summary.mavenReportedTestTime().toMillis());
        assertEquals(900, summary.junitObservedTestTime().toMillis());
        assertEquals(40, summary.junitSetupTime().toMillis());
        assertEquals(25, summary.junitTeardownTime().toMillis());
        assertEquals(1_000, summary.unaccountedLifecycleTime().orElseThrow().toMillis());
        assertEquals(
                "[TestSleuth] Timing: Maven tests 1000 ms, JUnit observed 900 ms, setup 40 ms, teardown 25 ms, lifecycle remainder 1000 ms",
                summary.consoleLine()
        );
    }

    @Test
    void clampsNegativeLifecycleRemainder() {
        MavenTimingSummary summary = MavenTimingSummary.from(
                Optional.of(Duration.ofMillis(500)),
                List.of(event("maven-1", "maven-test-report", 700))
        );

        assertEquals(0, summary.unaccountedLifecycleTime().orElseThrow().toMillis());
    }

    @Test
    void omitsRemainderWhenLifecycleWindowIsMissing() {
        MavenTimingSummary summary = MavenTimingSummary.from(
                Optional.empty(),
                List.of(event("maven-1", "maven-test-report", 700))
        );

        assertTrue(summary.unaccountedLifecycleTime().isEmpty());
        assertEquals("[TestSleuth] Timing: Maven tests 700 ms, JUnit observed 0 ms, setup 0 ms, teardown 0 ms", summary.consoleLine());
    }

    private static TestSleuthEvent event(String id, String collector, long durationMillis) {
        return event(id, EventKind.TEST_FINISHED, collector, durationMillis);
    }

    private static TestSleuthEvent event(String id, EventKind kind, String collector, long durationMillis) {
        return new TestSleuthEvent(
                new EventId(id),
                Optional.empty(),
                kind,
                new Subject(SubjectType.TEST_METHOD, id),
                Instant.EPOCH,
                0,
                Map.of(
                        "collector", collector,
                        "durationMillis", Long.toString(durationMillis)
                )
        );
    }
}

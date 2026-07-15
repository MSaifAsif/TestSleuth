package dev.testsleuth.maven;

import dev.testsleuth.core.event.EventId;
import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.Subject;
import dev.testsleuth.core.event.SubjectType;
import dev.testsleuth.core.event.TestSleuthEvent;
import dev.testsleuth.core.finding.Finding;
import dev.testsleuth.core.finding.FindingSeverity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MavenTimingFindingsTest {
    @Test
    void createsFindingsForSlowestMavenReportEvents() {
        TestSleuthMavenConfig config = TestSleuthMavenConfig.from(true, "summary", 100, 1_000, 10, true, false, 250);

        List<Finding> findings = new MavenTimingFindings(config).detect(List.of(
                event("fast", 10),
                event("slow", 1_250),
                event("medium", 500)
        ));

        assertEquals(2, findings.size());
        assertEquals("Slow observed test: slow", findings.get(0).title());
        assertEquals(FindingSeverity.HIGH, findings.get(0).severity());
        assertEquals(1_250, findings.get(0).observedCost().toMillis());
    }

    @Test
    void defaultThresholdSuppressesSubSecondFindings() {
        TestSleuthMavenConfig config = TestSleuthMavenConfig.from(true, "summary", 1_000, 5_000, 10, true, false, 250);

        List<Finding> findings = new MavenTimingFindings(config).detect(List.of(
                event("fast", 10),
                event("medium", 500)
        ));

        assertEquals(0, findings.size());
    }

    @Test
    void respectsMaxFindings() {
        TestSleuthMavenConfig config = TestSleuthMavenConfig.from(true, "summary", 1, 5_000, 1, true, false, 250);

        List<Finding> findings = new MavenTimingFindings(config).detect(List.of(
                event("slowest", 300),
                event("slower", 200),
                event("slow", 100)
        ));

        assertEquals(1, findings.size());
        assertEquals("Slow observed test: slowest", findings.get(0).title());
    }

    private static TestSleuthEvent event(String testName, long durationMillis) {
        return new TestSleuthEvent(
                new EventId("event-" + testName),
                Optional.empty(),
                EventKind.TEST_FINISHED,
                new Subject(SubjectType.TEST_METHOD, "ExampleTest." + testName),
                Instant.EPOCH,
                0,
                Map.of(
                        "collector", "maven-test-report",
                        "status", "passed",
                        "testName", testName,
                        "durationMillis", Long.toString(durationMillis),
                        "reportFile", "TEST-ExampleTest.xml"
                )
        );
    }
}

package dev.testsleuth.core.detector;

import dev.testsleuth.core.event.EventId;
import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.Subject;
import dev.testsleuth.core.event.SubjectType;
import dev.testsleuth.core.event.TestSleuthEvent;
import dev.testsleuth.core.finding.Finding;
import dev.testsleuth.core.finding.FindingSeverity;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SlowTestDetectorTest {
    @Test
    void createsFindingsForSlowestMatchingEvents() {
        SlowTestDetector detector = new SlowTestDetector(new SlowTestDetectorConfig(
                Duration.ofMillis(100),
                Duration.ofMillis(1_000),
                10,
                Optional.of("maven-test-report")
        ));

        List<Finding> findings = detector.detect(List.of(
                event("fast", 10, "maven-test-report"),
                event("slow", 1_250, "maven-test-report"),
                event("medium", 500, "maven-test-report"),
                event("junitSlow", 2_000, "junit5-listener")
        ));

        assertEquals(2, findings.size());
        assertEquals("Slow observed test: slow", findings.get(0).title());
        assertEquals(FindingSeverity.HIGH, findings.get(0).severity());
        assertEquals(1_250, findings.get(0).observedCost().toMillis());
    }

    @Test
    void joinsMatchingJUnitAndMavenEventsBeforeDetecting() {
        SlowTestDetector detector = new SlowTestDetector(new SlowTestDetectorConfig(
                Duration.ofMillis(100),
                Duration.ofMillis(1_000),
                10,
                Optional.of("maven-test-report")
        ));

        List<Finding> findings = detector.detect(List.of(
                event("slow", 1_200, "junit5-listener"),
                event("slow", 1_250, "maven-test-report")
        ));

        assertEquals(1, findings.size());
        assertEquals(1_250, findings.get(0).observedCost().toMillis());
        assertEquals("Joined collectors: junit5-listener, maven-test-report", findings.get(0).evidence().get(2));
        assertEquals("Module: module-a.", findings.get(0).evidence().get(3));
        assertEquals("Build run: run-1.", findings.get(0).evidence().get(4));
        assertEquals("Maven project: dev.testsleuth:sample:0.1.0.", findings.get(0).evidence().get(5));
        assertEquals("Process IDs: 12345.", findings.get(0).evidence().get(6));
        assertEquals("Fork numbers: 1.", findings.get(0).evidence().get(7));
        assertEquals("Test runners: surefire.", findings.get(0).evidence().get(8));
        assertEquals("Configured fork counts: 2.", findings.get(0).evidence().get(9));
    }

    @Test
    void respectsMaxFindings() {
        SlowTestDetector detector = new SlowTestDetector(new SlowTestDetectorConfig(
                Duration.ofMillis(1),
                Duration.ofMillis(5_000),
                1,
                Optional.empty()
        ));

        List<Finding> findings = detector.detect(List.of(
                event("slowest", 300, "maven-test-report"),
                event("slower", 200, "maven-test-report"),
                event("slow", 100, "maven-test-report")
        ));

        assertEquals(1, findings.size());
        assertEquals("Slow observed test: slowest", findings.get(0).title());
    }

    private static TestSleuthEvent event(String testName, long durationMillis, String collector) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("collector", collector);
        attributes.put("buildRunId", "run-1");
        attributes.put("moduleId", "module-a");
        attributes.put("projectGroupId", "dev.testsleuth");
        attributes.put("projectArtifactId", "sample");
        attributes.put("projectVersion", "0.1.0");
        attributes.put("processId", "12345");
        attributes.put("forkNumber", "1");
        attributes.put("testRunner", "surefire");
        attributes.put("testPlugin.forkCount", "2");
        attributes.put("status", "passed");
        attributes.put("testName", testName);
        attributes.put("testIdentity", "ExampleTest." + testName);
        attributes.put("durationMillis", Long.toString(durationMillis));
        attributes.put("reportFile", "TEST-ExampleTest.xml");

        return new TestSleuthEvent(
                new EventId("event-" + testName + "-" + collector),
                Optional.empty(),
                EventKind.TEST_FINISHED,
                new Subject(SubjectType.TEST_METHOD, "ExampleTest." + testName),
                Instant.EPOCH,
                0,
                attributes
        );
    }
}

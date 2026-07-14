package dev.testsleuth.maven;

import dev.testsleuth.core.event.TestSleuthEvent;
import dev.testsleuth.core.finding.Confidence;
import dev.testsleuth.core.finding.Finding;
import dev.testsleuth.core.finding.FindingCategory;
import dev.testsleuth.core.finding.FindingId;
import dev.testsleuth.core.finding.FindingSeverity;
import dev.testsleuth.core.finding.TimeSavingEstimate;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class MavenTimingFindings {
    private final TestSleuthMavenConfig config;

    MavenTimingFindings(TestSleuthMavenConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    List<Finding> detect(List<TestSleuthEvent> events) {
        Objects.requireNonNull(events, "events");
        if (!config.slowTestsDetectorEnabled() || config.maxFindings() == 0) {
            return List.of();
        }

        return events.stream()
                .filter(event -> "maven-test-report".equals(event.attributes().get("collector")))
                .filter(event -> "passed".equals(event.attributes().get("status")))
                .map(TimedTest::from)
                .filter(timedTest -> timedTest.duration().compareTo(config.slowTestThreshold()) >= 0)
                .sorted(Comparator.comparing(TimedTest::duration).reversed())
                .limit(config.maxFindings())
                .map(this::toFinding)
                .toList();
    }

    private Finding toFinding(TimedTest timedTest) {
        return new Finding(
                new FindingId("maven-slow-test:" + timedTest.subjectId()),
                "Slow observed test: " + timedTest.displayName(),
                FindingCategory.BUILD_RUNNER,
                severity(timedTest.duration()),
                Confidence.MEDIUM,
                timedTest.duration(),
                new TimeSavingEstimate(Duration.ZERO, timedTest.duration()),
                List.of(timedTest.subjectId()),
                List.of(
                        "Maven XML report recorded duration " + timedTest.duration().toMillis() + " ms.",
                        "Report file: " + timedTest.reportFile()
                ),
                "This is the highest-duration test observed in the Maven test report data.",
                "Inspect this test's setup, framework initialization, fixtures, waits, and external resources.",
                "Timing-only finding. It identifies where to investigate, but does not yet prove waste.",
                "Rerun TestSleuth after changes and compare this test's observed duration."
        );
    }

    private FindingSeverity severity(Duration duration) {
        if (duration.compareTo(config.verySlowTestThreshold()) >= 0) {
            return FindingSeverity.HIGH;
        }
        return FindingSeverity.MEDIUM;
    }

    private record TimedTest(String subjectId, String displayName, Duration duration, String reportFile) {
        private static TimedTest from(TestSleuthEvent event) {
            String durationMillis = event.attributes().getOrDefault("durationMillis", "0");
            return new TimedTest(
                    event.subject().identifier(),
                    event.attributes().getOrDefault("testName", event.subject().identifier()),
                    Duration.ofMillis(parseMillis(durationMillis)),
                    event.attributes().getOrDefault("reportFile", "unknown")
            );
        }

        private static long parseMillis(String value) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }
}

package dev.testsleuth.core.detector;

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
import java.util.Optional;

public final class SlowTestDetector implements TestSleuthDetector {
    private final SlowTestDetectorConfig config;

    public SlowTestDetector(SlowTestDetectorConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public List<Finding> detect(List<TestSleuthEvent> events) {
        Objects.requireNonNull(events, "events");
        if (config.maxFindings() == 0) {
            return List.of();
        }

        return new TestObservationJoiner().join(events).stream()
                .map(observation -> TimedTest.from(observation, config.collector()))
                .flatMap(Optional::stream)
                .filter(timedTest -> timedTest.duration().compareTo(config.slowTestThreshold()) >= 0)
                .sorted(Comparator.comparing(TimedTest::duration).reversed())
                .limit(config.maxFindings())
                .map(this::toFinding)
                .toList();
    }

    private Finding toFinding(TimedTest timedTest) {
        return new Finding(
                new FindingId("slow-test:" + timedTest.subjectId()),
                "Slow observed test: " + timedTest.displayName(),
                FindingCategory.BUILD_RUNNER,
                severity(timedTest.duration()),
                Confidence.MEDIUM,
                timedTest.duration(),
                new TimeSavingEstimate(Duration.ZERO, timedTest.duration()),
                List.of(timedTest.subjectId()),
                evidence(timedTest),
                "This is one of the highest-duration tests observed in event data.",
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

    private static List<String> evidence(TimedTest timedTest) {
        String source = timedTest.reportFile().isBlank()
                ? "Event data"
                : "Report file: " + timedTest.reportFile();
        return List.of(
                "Observed duration " + timedTest.duration().toMillis() + " ms.",
                source,
                "Joined collectors: " + String.join(", ", timedTest.collectors())
        );
    }

    private record TimedTest(
            String subjectId,
            String displayName,
            Duration duration,
            String reportFile,
            List<String> collectors
    ) {
        private static Optional<TimedTest> from(TestObservation observation, Optional<String> preferredCollector) {
            return observation.selectedFinishedEvent(preferredCollector)
                    .map(event -> new TimedTest(
                            observation.testIdentity(),
                            event.attributes().getOrDefault("testName",
                                    event.attributes().getOrDefault("displayName", observation.testIdentity())),
                            TestObservation.duration(event),
                            event.attributes().getOrDefault("reportFile", ""),
                            observation.collectors()
                    ));
        }
    }
}

package dev.testsleuth.maven;

import dev.testsleuth.core.detector.SlowTestDetector;
import dev.testsleuth.core.detector.SlowTestDetectorConfig;
import dev.testsleuth.core.event.TestSleuthEvent;
import dev.testsleuth.core.finding.Finding;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

        SlowTestDetectorConfig detectorConfig = new SlowTestDetectorConfig(
                config.slowTestThreshold(),
                config.verySlowTestThreshold(),
                config.maxFindings(),
                Optional.of("maven-test-report")
        );
        return new SlowTestDetector(detectorConfig).detect(events);
    }
}

package dev.testsleuth.core.detector;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public record SlowTestDetectorConfig(
        Duration slowTestThreshold,
        Duration verySlowTestThreshold,
        int maxFindings,
        Optional<String> collector
) {
    public SlowTestDetectorConfig {
        Objects.requireNonNull(slowTestThreshold, "slowTestThreshold");
        Objects.requireNonNull(verySlowTestThreshold, "verySlowTestThreshold");
        collector = Objects.requireNonNull(collector, "collector")
                .map(String::trim)
                .filter(value -> !value.isBlank());
        if (slowTestThreshold.isNegative() || slowTestThreshold.isZero()) {
            throw new IllegalArgumentException("slow test threshold must be positive");
        }
        if (verySlowTestThreshold.compareTo(slowTestThreshold) < 0) {
            throw new IllegalArgumentException("very slow test threshold must be greater than or equal to slow test threshold");
        }
        if (maxFindings < 0) {
            throw new IllegalArgumentException("max findings must not be negative");
        }
    }
}

package dev.testsleuth.core.finding;

import java.time.Duration;
import java.util.Objects;

public record TimeSavingEstimate(Duration lowerBound, Duration upperBound) {
    public TimeSavingEstimate {
        Objects.requireNonNull(lowerBound, "lowerBound");
        Objects.requireNonNull(upperBound, "upperBound");
        if (lowerBound.isNegative() || upperBound.isNegative()) {
            throw new IllegalArgumentException("saving estimate must not be negative");
        }
        if (lowerBound.compareTo(upperBound) > 0) {
            throw new IllegalArgumentException("lower bound must not exceed upper bound");
        }
    }
}


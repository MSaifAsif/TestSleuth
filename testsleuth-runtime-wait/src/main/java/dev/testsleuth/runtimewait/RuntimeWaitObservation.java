package dev.testsleuth.runtimewait;

import dev.testsleuth.core.event.RuntimeWaitEventAttributes;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record RuntimeWaitObservation(
        String operation,
        Duration observedDuration,
        Optional<Duration> configuredTimeout,
        RuntimeWaitEventAttributes.WaitOutcome outcome,
        Thread thread,
        Instant wallClockTime,
        long monotonicNanos,
        long collectorOverheadNanos,
        Optional<String> sourceLocation,
        Optional<String> stackTop
) {
    public RuntimeWaitObservation {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(observedDuration, "observedDuration");
        configuredTimeout = Objects.requireNonNull(configuredTimeout, "configuredTimeout");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(thread, "thread");
        Objects.requireNonNull(wallClockTime, "wallClockTime");
        sourceLocation = Objects.requireNonNull(sourceLocation, "sourceLocation").filter(value -> !value.isBlank());
        stackTop = Objects.requireNonNull(stackTop, "stackTop").filter(value -> !value.isBlank());
        if (operation.isBlank()) {
            throw new IllegalArgumentException("operation must not be blank");
        }
        if (observedDuration.isNegative()) {
            throw new IllegalArgumentException("observed duration must not be negative");
        }
        configuredTimeout.ifPresent(timeout -> {
            if (timeout.isNegative()) {
                throw new IllegalArgumentException("configured timeout must not be negative");
            }
        });
        if (monotonicNanos < 0) {
            throw new IllegalArgumentException("monotonic nanos must not be negative");
        }
        if (collectorOverheadNanos < 0) {
            throw new IllegalArgumentException("collector overhead must not be negative");
        }
    }

    public static RuntimeWaitObservation finished(
            String operation,
            Duration observedDuration,
            Optional<Duration> configuredTimeout,
            RuntimeWaitEventAttributes.WaitOutcome outcome,
            long collectorOverheadNanos
    ) {
        return new RuntimeWaitObservation(
                operation,
                observedDuration,
                configuredTimeout,
                outcome,
                Thread.currentThread(),
                Instant.now(),
                System.nanoTime(),
                collectorOverheadNanos,
                Optional.empty(),
                Optional.empty()
        );
    }
}

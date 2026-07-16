package dev.testsleuth.core.event;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RuntimeWaitEventAttributes {
    public static final String COLLECTOR = "runtime-wait-collector";
    public static final String OPERATION = "waitOperation";
    public static final String OBSERVED_DURATION_MILLIS = "durationMillis";
    public static final String CONFIGURED_TIMEOUT_MILLIS = "configuredTimeoutMillis";
    public static final String OUTCOME = "waitOutcome";
    public static final String THREAD_NAME = "threadName";
    public static final String THREAD_ID = "threadId";
    public static final String SOURCE_LOCATION = "sourceLocation";
    public static final String STACK_TOP = "stackTop";
    public static final String COLLECTOR_OVERHEAD_NANOS = "collectorOverheadNanos";

    private RuntimeWaitEventAttributes() {
    }

    public static Map<String, String> finished(
            String operation,
            Duration observedDuration,
            Optional<Duration> configuredTimeout,
            WaitOutcome outcome,
            Thread thread
    ) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(observedDuration, "observedDuration");
        Objects.requireNonNull(configuredTimeout, "configuredTimeout");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(thread, "thread");
        if (operation.isBlank()) {
            throw new IllegalArgumentException("operation must not be blank");
        }
        if (observedDuration.isNegative()) {
            throw new IllegalArgumentException("observed duration must not be negative");
        }

        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("collector", COLLECTOR);
        attributes.put(OPERATION, operation);
        attributes.put(OBSERVED_DURATION_MILLIS, Long.toString(observedDuration.toMillis()));
        configuredTimeout
                .filter(timeout -> !timeout.isNegative())
                .map(Duration::toMillis)
                .map(value -> Long.toString(value))
                .ifPresent(value -> attributes.put(CONFIGURED_TIMEOUT_MILLIS, value));
        attributes.put(OUTCOME, outcome.value());
        attributes.put(THREAD_NAME, thread.getName());
        attributes.put(THREAD_ID, Long.toString(thread.getId()));
        return Map.copyOf(attributes);
    }

    public enum WaitOutcome {
        COMPLETED("completed"),
        TIMED_OUT("timed-out"),
        INTERRUPTED("interrupted"),
        FAILED("failed");

        private final String value;

        WaitOutcome(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}

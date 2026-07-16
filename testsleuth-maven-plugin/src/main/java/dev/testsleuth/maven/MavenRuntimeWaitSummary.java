package dev.testsleuth.maven;

import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.RuntimeWaitEventAttributes;
import dev.testsleuth.core.event.TestSleuthEvent;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

record MavenRuntimeWaitSummary(
        int eventCount,
        Duration observedWaitTime,
        Duration collectorOverhead
) {
    MavenRuntimeWaitSummary {
        Objects.requireNonNull(observedWaitTime, "observedWaitTime");
        Objects.requireNonNull(collectorOverhead, "collectorOverhead");
        if (eventCount < 0) {
            throw new IllegalArgumentException("event count must not be negative");
        }
        if (observedWaitTime.isNegative()) {
            throw new IllegalArgumentException("observed wait time must not be negative");
        }
        if (collectorOverhead.isNegative()) {
            throw new IllegalArgumentException("collector overhead must not be negative");
        }
    }

    static MavenRuntimeWaitSummary from(List<TestSleuthEvent> events) {
        Objects.requireNonNull(events, "events");

        int count = 0;
        long observedMillis = 0;
        long overheadNanos = 0;
        for (TestSleuthEvent event : events) {
            if (event.kind() != EventKind.WAIT_FINISHED
                    || !RuntimeWaitEventAttributes.COLLECTOR.equals(event.attributes().get("collector"))) {
                continue;
            }
            count++;
            observedMillis += longAttribute(event, RuntimeWaitEventAttributes.OBSERVED_DURATION_MILLIS).orElse(0L);
            overheadNanos += longAttribute(event, RuntimeWaitEventAttributes.COLLECTOR_OVERHEAD_NANOS).orElse(0L);
        }
        return new MavenRuntimeWaitSummary(count, Duration.ofMillis(observedMillis), Duration.ofNanos(overheadNanos));
    }

    boolean hasEvents() {
        return eventCount > 0;
    }

    private static Optional<Long> longAttribute(TestSleuthEvent event, String attributeName) {
        try {
            return Optional.ofNullable(event.attributes().get(attributeName)).map(Long::parseLong);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}

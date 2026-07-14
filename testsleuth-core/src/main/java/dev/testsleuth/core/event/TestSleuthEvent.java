package dev.testsleuth.core.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record TestSleuthEvent(
        EventId id,
        Optional<EventId> parentId,
        EventKind kind,
        Subject subject,
        Instant wallClockTime,
        long monotonicNanos,
        Map<String, String> attributes
) {
    public TestSleuthEvent {
        Objects.requireNonNull(id, "id");
        parentId = Objects.requireNonNull(parentId, "parentId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(wallClockTime, "wallClockTime");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
        if (monotonicNanos < 0) {
            throw new IllegalArgumentException("monotonic nanos must not be negative");
        }
    }
}


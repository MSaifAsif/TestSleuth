package dev.testsleuth.core.event;

import java.util.Objects;
import java.util.UUID;

public record EventId(String value) {
    public EventId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("event id must not be blank");
        }
    }

    public static EventId random() {
        return new EventId(UUID.randomUUID().toString());
    }
}


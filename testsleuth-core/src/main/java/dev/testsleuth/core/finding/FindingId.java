package dev.testsleuth.core.finding;

import java.util.Objects;
import java.util.UUID;

public record FindingId(String value) {
    public FindingId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("finding id must not be blank");
        }
    }

    public static FindingId random() {
        return new FindingId(UUID.randomUUID().toString());
    }
}


package dev.testsleuth.core.event;

import java.util.Objects;

public record Subject(SubjectType type, String identifier) {
    public Subject {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(identifier, "identifier");
        if (identifier.isBlank()) {
            throw new IllegalArgumentException("subject identifier must not be blank");
        }
    }
}


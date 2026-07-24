package dev.testsleuth.core.finding;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record Finding(
        FindingId id,
        String title,
        FindingCategory category,
        FindingSeverity severity,
        Confidence confidence,
        EvidenceType evidenceType,
        AttributionScope attributionScope,
        Duration observedCost,
        TimeSavingEstimate recoverableTime,
        List<String> affectedSubjects,
        List<String> evidence,
        String rootCause,
        String recommendedAction,
        String tradeOffs,
        String verificationMethod
) {
    public Finding {
        Objects.requireNonNull(id, "id");
        requireText(title, "title");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(evidenceType, "evidenceType");
        Objects.requireNonNull(attributionScope, "attributionScope");
        Objects.requireNonNull(observedCost, "observedCost");
        Objects.requireNonNull(recoverableTime, "recoverableTime");
        affectedSubjects = List.copyOf(Objects.requireNonNull(affectedSubjects, "affectedSubjects"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        requireText(rootCause, "rootCause");
        requireText(recommendedAction, "recommendedAction");
        requireText(tradeOffs, "tradeOffs");
        requireText(verificationMethod, "verificationMethod");
        if (observedCost.isNegative()) {
            throw new IllegalArgumentException("observed cost must not be negative");
        }
    }

    private static void requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}

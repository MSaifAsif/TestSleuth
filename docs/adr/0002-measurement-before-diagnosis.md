# ADR 0002: Measurement Before Diagnosis

## Status

Accepted

## Context

TestSleuth's product advantage depends on turning observed facts into ranked, explainable findings. If collectors directly produce advice, the system becomes difficult to test, replay, and extend.

## Decision

Collectors will emit normalized events. Detectors will consume event streams, timelines, and cost graphs to produce findings.

The initial core model includes:

- Event identity and parent-child relationships.
- Subject type and identifier.
- Wall-clock and monotonic timestamps.
- Attribute maps for collector-specific facts.
- Finding severity, confidence, category, observed cost, recoverable-time range, evidence, recommendation, trade-offs, and verification method.

## Consequences

Detector tests can run offline against recorded event streams. New framework integrations can be added without changing the report and recommendation model.


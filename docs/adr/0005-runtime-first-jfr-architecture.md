# ADR 0005: Runtime-First JFR Architecture

## Status

Accepted

## Context

The Version 2 roadmap redefines TestSleuth as a test-performance diagnosis and optimization profiler. The product should explain what the JVM actually did during a test run, attribute meaningful costs to tests and lifecycle phases, and produce ranked, evidence-backed actions.

The existing Maven, JUnit, event, finding, source scanning, runtime wait, report, and sample foundations remain useful. However, source scanning and timing-only findings are not sufficient as the primary evidence engine because they can identify potential patterns without proving execution, observed duration, repetition, ownership, or runtime cause.

## Decision

Pivot the architecture to runtime-first diagnosis:

- Java Flight Recorder is the primary deep-diagnosis engine.
- One recording should be collected per Maven Surefire/Failsafe fork and, later, per Gradle test worker JVM.
- JUnit lifecycle collectors remain the test-boundary source of truth and should eventually emit or align with JFR custom events.
- Source and build scanning remain as enrichment and remediation layers, not primary diagnosis.
- Runtime wait collection and selective Java-agent/Byte Buddy instrumentation are supporting mechanisms used where JFR and framework hooks cannot establish enough causality.
- Static-only findings must be labeled as potential and must not claim measured recoverable time.
- Findings must grow evidence and attribution concepts so the report can distinguish measured, correlated, inferred, and potential evidence, as well as direct, shared, framework, fork-wide, build-wide, and unclassified cost.

## Consequences

Near-term work shifts from broadening source detectors to establishing the Version 2 evidence model and JFR recording path.

The existing source wait and framework-initialization detectors should be retained, but they should be demoted over time to source/build enrichment or potential findings unless connected to runtime evidence.

The runtime wait module remains useful as a direct collector and future selective instrumentation support, but automatic wait interception should not displace JFR as the primary runtime profiler.

Gradle remains deferred until the Maven JFR path proves the core model, recording lifecycle, attribution, and reporting approach.

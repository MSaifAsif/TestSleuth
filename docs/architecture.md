# Architecture

TestSleuth separates measurement from diagnosis.

```text
Collectors
    -> Event stream
    -> Timeline
    -> Cost graph
    -> Detector engine
    -> Findings
    -> Report
    -> Verification
```

Collectors emit normalized facts. Detectors consume those facts later and produce evidence-backed findings. This keeps framework-specific instrumentation separate from the shared event model and makes detector behavior testable without rerunning a user's suite.

## Initial Modules

- `testsleuth-core`: immutable event and finding model.
- `testsleuth-junit5`: JUnit Platform lifecycle listener.
- `testsleuth-report`: report model and rendering entry points.
- `testsleuth-maven-plugin`: Maven plugin goals for instrumentation, report generation, aggregation, CI logs, and machine-readable output.

## Pending Modules

- `testsleuth-spring`
- `testsleuth-testcontainers`
- `testsleuth-flyway`
- `testsleuth-liquibase`
- `testsleuth-awaitility`
- `testsleuth-cli`
- `testsleuth-benchmarks`
- `testsleuth-samples`

Gradle plugin support remains a product roadmap item, but current implementation work is intentionally focused on the Maven Phase 2 path.

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
- `testsleuth-report`: report model and rendering entry points.
- `testsleuth-maven-plugin`: Maven plugin shell and future Surefire/Failsafe integration point.

## Pending Modules

- `testsleuth-junit5`
- `testsleuth-spring`
- `testsleuth-testcontainers`
- `testsleuth-flyway`
- `testsleuth-liquibase`
- `testsleuth-awaitility`
- `testsleuth-gradle-plugin`
- `testsleuth-cli`
- `testsleuth-benchmarks`
- `testsleuth-samples`


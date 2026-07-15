# Codex Context

This repository is TestSleuth, a local-first diagnostic tool for slow Java test suites.

## Current Direction

- Focus on Maven Phase 2 only for now.
- Gradle remains a product roadmap item, but do not start Gradle work unless explicitly asked.
- The current product goal is a Maven plugin that observes test runs, aggregates results, and produces useful local and CI-friendly reports.
- Prefer moving the Maven plugin toward normal lifecycle usage over adding one-off manual commands.

## Current Capabilities

- Maven reactor with Java 17.
- Core event and finding models in `testsleuth-core`.
- JUnit Platform listener in `testsleuth-junit5`.
- Basic HTML rendering in `testsleuth-report`.
- Maven plugin goals in `testsleuth-maven-plugin`:
  - `testsleuth:instrument`
  - `testsleuth:report`
  - `testsleuth:aggregate-report`
- Maven report output:
  - `target/testsleuth/events.json`
  - `target/testsleuth/findings.json`
  - `target/testsleuth/index.html`
- CI-oriented console summary with optional finding detail.
- Slow-test detector using joined JUnit lifecycle and Maven XML events.
- Opt-in fixed-wait source detector for direct `Thread.sleep(...)` calls.
- Maven XML report events include Surefire/Failsafe runner metadata and configured fork settings when available.
- Slow JUnit 5 Maven sample bound into the normal Maven lifecycle.

## Important Commands

Build and test the full repository:

```bash
mvn verify
```

Run the sample through normal Maven lifecycle:

```bash
mvn -pl testsleuth-samples/slow-junit5-maven verify
```

Run the sample with fixed-wait source detection and detailed logs:

```bash
mvn -pl testsleuth-samples/slow-junit5-maven verify \
  -Dtestsleuth.detectors.fixedWaits=true \
  -Dtestsleuth.console.detail=findings
```

Install the local snapshot only when needed for standalone plugin resolution:

```bash
mvn install -DskipTests
```

In sandboxed Codex sessions, `mvn install` may need approval because it writes to `~/.m2`.

## Repository Map

- `testsleuth-core`: framework-neutral event, finding, detector, and JSON model.
- `testsleuth-junit5`: JUnit Platform lifecycle listener.
- `testsleuth-report`: HTML report renderer.
- `testsleuth-maven-plugin`: Maven instrumentation, report generation, aggregation, console output, and Maven-specific detectors.
- `testsleuth-samples/slow-junit5-maven`: intentionally slow Maven/JUnit 5 sample.
- `docs/implementation-start.md`: current implementation tracker.
- `docs/architecture.md`: architectural overview.
- `docs/adr/`: architecture decision records.
- `project-management/`: ignored planning material, including the phase roadmap.

## Engineering Rules

- Keep changes aligned with the existing Maven-first architecture.
- Keep detector logic testable without rerunning a real user suite.
- Prefer normalized event data and reusable core models over Maven-only shortcuts.
- Keep source scanning opt-in unless the cost is clearly low and documented.
- Keep console output brief and useful for CI logs.
- Update `CHANGELOG.md` for user-visible fixes, features, behavior changes, and notable docs changes.
- Update `docs/implementation-start.md` when changing roadmap status or next steps.
- Do not add GitHub Actions right now.
- Do not remove user changes or rewrite unrelated files.

## Current Next Steps

1. Add Spring/framework initialization sample coverage.
2. Add richer source detectors for polling libraries and framework-specific waits.
3. Improve wall-clock/build-phase timing coverage.

## Verification Expectations

- Run `mvn verify` for code changes.
- For Maven plugin behavior changes, also run the sample lifecycle command.
- If a change affects local plugin resolution, run `mvn install -DskipTests` before the standalone sample command.
- Mention any verification that could not be run.

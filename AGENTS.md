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
- JUnit 4 listener in `testsleuth-junit4`.
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
- Opt-in polling-wait source detector for direct `Thread.sleep(...)` calls inside nearby loops.
- Opt-in framework-initialization candidate detector that correlates application-context source indicators with observed class duration.
- Maven XML report events include Surefire/Failsafe runner metadata and configured fork settings when available.
- Maven reports include an observed lifecycle window from `testsleuth:instrument` to `testsleuth:report`.
- Maven console and HTML summaries include initial timing reconciliation for Maven-reported test time, JUnit-observed test time, setup time, teardown time, and lifecycle remainder.
- Maven console output includes TestSleuth report overhead.
- HTML reports include a run summary scorecard, top opportunity, and category breakdown.
- JUnit 5 instrumentation includes a Jupiter extension for per-test setup and teardown phase events.
- JUnit 4 instrumentation includes a RunListener and `junit4-events.json` merge path.
- Slow JUnit 5 Maven sample includes Spring-style framework initialization scenarios without external Spring dependencies.
- Slow Maven sample includes a legacy JUnit 4 test through the Vintage engine.
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
  -Dtestsleuth.detectors.pollingWaits=true \
  -Dtestsleuth.detectors.frameworkInitialization=true \
  -Dtestsleuth.console.detail=findings
```

Install the local snapshot only when needed for standalone plugin resolution:

```bash
mvn install -DskipTests
```

In sandboxed Codex sessions, `mvn install` may need approval because it writes to `~/.m2`.

## Repository Map

- `testsleuth-core`: framework-neutral event, finding, detector, and JSON model.
- `testsleuth-junit4`: JUnit 4 RunListener lifecycle collector.
- `testsleuth-junit5`: JUnit Platform lifecycle listener.
- `testsleuth-report`: HTML report renderer.
- `testsleuth-maven-plugin`: Maven instrumentation, report generation, aggregation, console output, and Maven-specific detectors.
- `testsleuth-samples/slow-junit5-maven`: intentionally slow Maven/JUnit 5 sample.
- `README.md`: current architecture, usage, and repository overview.
- `CHANGELOG.md`: notable user-visible changes.
- `docs/adr/`: architecture decision records.
- `project-management/`: ignored planning material, including the phase roadmap.

## Engineering Rules

- Keep changes aligned with the existing Maven-first architecture.
- Keep detector logic testable without rerunning a real user suite.
- Prefer normalized event data and reusable core models over Maven-only shortcuts.
- Keep source scanning opt-in unless the cost is clearly low and documented.
- Keep console output brief and useful for CI logs.
- Update `CHANGELOG.md` for user-visible fixes, features, behavior changes, and notable docs changes.
- Keep README aligned when architecture, usage, or repository layout changes.
- Do not add GitHub Actions right now.
- Do not remove user changes or rewrite unrelated files.

## Current Next Steps

1. Validate JUnit 4 auto-injection against a pure JUnit 4 Maven sample or external project.
2. Add richer source detectors for polling libraries and framework-specific waits.
3. Improve wall-clock/build-phase timing coverage with explicit discovery buckets and richer framework-initialization events.
4. Add real Spring Boot collector/sample coverage when external dependencies are acceptable.

## Verification Expectations

- Run `mvn verify` for code changes.
- For Maven plugin behavior changes, also run the sample lifecycle command.
- If a change affects local plugin resolution, run `mvn install -DskipTests` before the standalone sample command.
- Mention any verification that could not be run.

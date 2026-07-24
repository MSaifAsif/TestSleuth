# Codex Context

This repository is TestSleuth, a local-first diagnostic tool for slow Java test suites.

## Current Direction

- Align with `project-management/Roadmapv2.md`: runtime-first diagnosis, with Java Flight Recorder as the primary deep-diagnosis engine.
- Continue Maven-first implementation for now. Gradle remains a product roadmap item, but do not start Gradle work unless explicitly asked.
- The current product goal is a Maven plugin that observes test runs, records runtime evidence, attributes costs to tests/lifecycle phases, and produces useful local and CI-friendly reports.
- Source scanning is enrichment/remediation support. Do not treat source-only findings as measured runtime diagnoses.
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
- Runtime wait event contract and direct runtime wait collector API.
- Opt-in Maven runtime wait event-file wiring and runtime wait findings for direct collector events.
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
- Pure JUnit 4 Maven sample validates the legacy Surefire JUnit 4 provider and `junit4-listener` path. It declares Surefire `properties.listener` statically because the legacy provider does not expose that setting as a Maven user property.
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

Run the pure JUnit 4 sample through normal Maven lifecycle:

```bash
mvn -pl testsleuth-samples/slow-junit4-maven verify
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
- `testsleuth-samples/slow-junit4-maven`: intentionally slow pure Maven/JUnit 4 sample.
- `README.md`: current architecture, usage, and repository overview.
- `CHANGELOG.md`: notable user-visible changes.
- `docs/adr/`: architecture decision records.
- `project-management/`: ignored planning material, including the phase roadmap.

## Engineering Rules

- Format Java source with IntelliJ IDEA before verification. `.idea/codeStyles/Project.xml` is the shared project code-style scheme and `.editorconfig` is its portable baseline: four-space indentation, eight-space continuation indentation, LF line endings, trailing-whitespace removal, and a 120-column right margin.
- Favor static imports for named constants when they improve readability. Do not use wildcard static imports, and keep method calls qualified unless a local convention makes a static import clearer.
- Keep exactly one top-level class, record, interface, or enum declaration in each `.java` file. Name the file after that declaration; nested implementation details remain scoped to their owning type.
- Keep changes aligned with the existing Maven-first architecture.
- Keep changes aligned with the Version 2 runtime-first/JFR-first architecture.
- Keep detector logic testable without rerunning a real user suite.
- Prefer normalized event data and reusable core models over Maven-only shortcuts.
- Keep source scanning opt-in unless the cost is clearly low and documented. Label source-only findings as potential/static until runtime evidence supports them.
- Keep console output brief and useful for CI logs.
- Update `CHANGELOG.md` for user-visible fixes, features, behavior changes, and notable docs changes.
- Keep README aligned when architecture, usage, or repository layout changes.
- End implementation handoffs with what changed in the current iteration, relevant diff/log excerpts, documentation updates, and verification commands/results.
- Do not add GitHub Actions right now.
- Do not remove user changes or rewrite unrelated files.

## Current Next Steps

1. Add Version 2 evidence type, attribution scope, and confidence fields to findings.
2. Add normalized JFR event models and a Maven JFR recording plan for Surefire/Failsafe forks.
3. Implement one JFR recording per Maven Surefire fork, preserving user JVM arguments and reporting recording status/overhead.
4. Implement one JFR recording per Maven Failsafe fork and aggregate recording discovery.
5. Parse recordings and join sequential runtime events to JUnit/Maven test windows.

## Verification Expectations

- Run `mvn verify` for code changes.
- For Maven plugin behavior changes, also run the sample lifecycle command.
- If a change affects local plugin resolution, run `mvn install -DskipTests` before the standalone sample command.
- Mention any verification that could not be run.

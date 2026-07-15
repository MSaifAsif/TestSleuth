# TestSleuth

TestSleuth is a local-first diagnostic tool for slow test suites.

The project is currently in the Maven measurement foundation phase. The immediate goal is to make the Maven plugin observe test runs, aggregate results, and produce useful local and CI-friendly reports.

## Current Scope

- Java 17+ codebase.
- Maven reactor as the initial build foundation.
- Core event and finding model.
- Basic report rendering module.
- Maven plugin goals for instrumentation, reporting, and aggregation.
- Architecture decision records under `docs/adr`.

## Build

```bash
mvn verify
```

## Current Maven Report Output

The current `testsleuth:report` goal is an early Maven measurement foundation. It scans Maven Surefire and Failsafe XML reports, merges JUnit lifecycle events when available, writes `target/testsleuth/events.json`, writes machine-readable `target/testsleuth/findings.json`, and writes a basic `target/testsleuth/index.html`.

To enable the current JUnit listener before tests run:

```bash
mvn testsleuth:instrument test testsleuth:report
```

Projects can also bind the plugin into the Maven lifecycle so normal `mvn verify` runs instrumentation before tests and writes the report during `verify`.

The report currently includes timing-based findings for the slowest tests observed in Maven test reports. Findings include joined JUnit/Maven collector evidence and run context such as module, build run, Maven project, process, and fork when available. `findings.json` contains the same finding payload for CI systems and other tooling. These are investigation findings, not yet root-cause diagnoses.

## Sample Project

The repository includes an intentionally slow Maven/JUnit 5 sample:

```bash
mvn -pl testsleuth-samples/slow-junit5-maven verify
```

The sample produces a default slow-test finding without lowering the `1000ms` threshold.

### Maven Report Configuration

The report goal is configurable with Maven properties:

```bash
mvn testsleuth:instrument test testsleuth:report \
  -Dtestsleuth.threshold.slowTestMillis=1000 \
  -Dtestsleuth.threshold.verySlowTestMillis=5000 \
  -Dtestsleuth.threshold.fixedWaitMillis=250 \
  -Dtestsleuth.findings.max=10 \
  -Dtestsleuth.detectors.fixedWaits=false \
  -Dtestsleuth.console.detail=summary
```

Current options:

- `testsleuth.console.enabled`: `true` by default.
- `testsleuth.console.detail`: `quiet`, `summary`, or `findings`; default is `summary`. `findings` adds one compact log line per finding with module, fork, and collector context when available.
- `testsleuth.threshold.slowTestMillis`: minimum duration for slow-test findings; default is `1000`.
- `testsleuth.threshold.verySlowTestMillis`: threshold for high-severity slow-test findings; default is `5000`.
- `testsleuth.threshold.fixedWaitMillis`: minimum direct `Thread.sleep(...)` duration for fixed-wait source findings; default is `250`.
- `testsleuth.findings.max`: maximum timing findings to show; default is `10`.
- `testsleuth.detectors.slowTests`: enables the current slow-test detector; default is `true`.
- `testsleuth.detectors.fixedWaits`: enables opt-in test-source scanning for direct `Thread.sleep(...)` calls; default is `false`.

## Current JUnit 5 Listener

The `testsleuth-junit5` module provides a JUnit Platform `TestExecutionListener`. When it is present on the test runtime classpath and the `testsleuth.junit.events.file` system property is set, it writes JUnit lifecycle events to that JSON file.

The Maven `testsleuth:instrument` goal adds the listener dependency and sets the required JUnit Platform properties for the current Maven session.

## License

TestSleuth is licensed under the Apache License, Version 2.0. See [LICENSE.txt](LICENSE.txt) and [NOTICE](NOTICE).

## Repository Layout

```text
testsleuth-core
testsleuth-report
testsleuth-maven-plugin
docs
```

Additional Maven collectors, detector modules, benchmarks, samples, and CLI modules will be added as the roadmap phases become active. Gradle support is intentionally deferred for now.

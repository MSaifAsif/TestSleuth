# TestSleuth

TestSleuth is a local-first diagnostic tool for slow test suites.

The project is currently in the repository-foundation phase. The immediate goal is to create the core event and finding model, then build Maven and Gradle plugin entry points that can observe a whole test run and produce local reports.

## Current Scope

- Java 17+ codebase.
- Maven reactor as the initial build foundation.
- Core event and finding model.
- Basic report rendering module.
- Maven plugin shell.
- Architecture decision records under `docs/adr`.

The product roadmap lives in [TestSleuth_Phase_Roadmap.md](TestSleuth_Phase_Roadmap.md).

## Build

```bash
mvn verify
```

## Current Maven Report Output

The current `testsleuth:report` goal is an early shell. It scans Maven Surefire and Failsafe XML reports, merges JUnit lifecycle events when available, writes `target/testsleuth/events.json`, and writes a basic `target/testsleuth/index.html`.

To enable the current JUnit listener before tests run:

```bash
mvn testsleuth:instrument test testsleuth:report
```

Full whole-build timing and automatic lifecycle binding are still upcoming roadmap work.

The report currently includes timing-based findings for the slowest tests observed in Maven test reports. These are investigation findings, not yet root-cause diagnoses.

## Sample Project

The repository includes an intentionally slow Maven/JUnit 5 sample:

```bash
mvn -pl testsleuth-samples/slow-junit5-maven \
  dev.testsleuth:testsleuth-maven-plugin:0.1.0-SNAPSHOT:instrument \
  test \
  dev.testsleuth:testsleuth-maven-plugin:0.1.0-SNAPSHOT:report
```

The sample produces a default slow-test finding without lowering the `1000ms` threshold.

### Maven Report Configuration

The report goal is configurable with Maven properties:

```bash
mvn testsleuth:instrument test testsleuth:report \
  -Dtestsleuth.threshold.slowTestMillis=1000 \
  -Dtestsleuth.threshold.verySlowTestMillis=5000 \
  -Dtestsleuth.findings.max=10 \
  -Dtestsleuth.console.detail=summary
```

Current options:

- `testsleuth.console.enabled`: `true` by default.
- `testsleuth.console.detail`: `quiet`, `summary`, or `findings`; default is `summary`.
- `testsleuth.threshold.slowTestMillis`: minimum duration for slow-test findings; default is `1000`.
- `testsleuth.threshold.verySlowTestMillis`: threshold for high-severity slow-test findings; default is `5000`.
- `testsleuth.findings.max`: maximum timing findings to show; default is `10`.
- `testsleuth.detectors.slowTests`: enables the current slow-test detector; default is `true`.

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

Additional collectors, detector modules, Gradle plugin support, benchmarks, samples, and CLI modules will be added as the roadmap phases become active.

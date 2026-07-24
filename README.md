# TestSleuth

TestSleuth is a local-first test-performance diagnosis and optimization profiler.

The product direction is runtime-first: TestSleuth should record what the JVM actually did during a test run, attribute meaningful costs to tests and lifecycle phases, and produce ranked, evidence-backed actions to reduce suite duration.

The current implementation has completed Version 3 Phase 1 and the Maven capture slice of Phase 2. Maven/JUnit lifecycle collection, report generation, source wait scanning, runtime wait event wiring, and samples remain reusable foundations. With opt-in Java Flight Recorder (JFR) capture, TestSleuth now retains one bounded raw recording for each Maven test JVM. JFR parsing and per-test runtime-cause attribution are the next steps. Source scanning remains enrichment and remediation support, not the primary evidence source.

## Current Scope

- Java 17+ codebase.
- Maven reactor as the initial build foundation.
- Core event and finding model.
- Basic report rendering module.
- Maven plugin goals for instrumentation, reporting, and aggregation.
- Architecture decision records under `docs/adr`, including the runtime wait event direction and runtime-first JFR pivot.
- Findings labeled by evidence type (`MEASURED`, `CORRELATED`, `INFERRED`, or `POTENTIAL`) and attribution scope.

## Build

```bash
mvn verify
```

## Architecture

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

Current modules:

- `testsleuth-core`: framework-neutral event, finding, detector, and JSON model.
- `testsleuth-junit4`: JUnit 4 RunListener lifecycle collector.
- `testsleuth-junit5`: JUnit Platform lifecycle listener.
- `testsleuth-report`: HTML report renderer.
- `testsleuth-maven-plugin`: Maven instrumentation, report generation, aggregation, CI logs, and machine-readable output.
- `testsleuth-samples/slow-junit5-maven`: intentionally slow Maven/JUnit 5 sample.
- `testsleuth-samples/slow-junit4-maven`: intentionally slow pure Maven/JUnit 4 sample.

Planned Maven-first expansion areas:

- `testsleuth-spring`
- `testsleuth-testcontainers`
- `testsleuth-flyway`
- `testsleuth-liquibase`
- `testsleuth-awaitility`
- `testsleuth-cli`
- `testsleuth-benchmarks`

Gradle plugin support remains a product roadmap item, but current implementation work is intentionally focused on proving the Maven runtime/JFR path first.

## Current Maven Report Output

The current `testsleuth:report` goal is an early Maven measurement foundation. It scans Maven Surefire and Failsafe XML reports, merges JUnit lifecycle events when available, writes `target/testsleuth/events.json`, writes machine-readable `target/testsleuth/findings.json`, and writes a basic `target/testsleuth/index.html`.

To enable the current JUnit listener before tests run:

```bash
mvn testsleuth:instrument test testsleuth:report
```

Projects can also bind the plugin into the Maven lifecycle so normal `mvn verify` runs instrumentation before tests and writes the report during `verify`.

The report currently includes timing-based findings for the slowest tests observed in Maven test reports. Findings include joined JUnit/Maven collector evidence and run context such as module, build run, Maven project, process, and fork when available. `findings.json` contains the same finding payload for CI systems and other tooling. These are foundation-level investigation findings, not yet the runtime-cause diagnoses described in the Version 3 roadmap. `findings.json` and the HTML report identify each finding's evidence type and attribution scope so source-only patterns are visibly distinct from runtime observations.

When runtime wait collection is enabled and runtime wait events are present, reports also produce measured fork-wide wait findings above the configured fixed-wait threshold. Source wait and source/timing framework detectors remain separate and opt-in; they are labeled `POTENTIAL` with no measured or recoverable cost until a future JFR-backed run connects them to runtime evidence.

Console and HTML summaries also include an initial timing reconciliation: Maven-reported test time, JUnit-observed test time, JUnit setup and teardown time, and unclassified observed Maven lifecycle time when available. Console output includes named timing buckets for the lifecycle window, Maven test execution, JUnit observed execution, setup, teardown, unclassified lifecycle time, and report generation. When runtime wait events are present, console output also reports runtime wait event count, observed wait time, and collector overhead. The unclassified lifecycle bucket is a coarse signal for build setup, compilation, runner overhead, framework initialization, or other work not yet attributed to a specific test phase. The HTML report includes a run summary scorecard, top opportunity, and category breakdown before the detailed findings table.

When `testsleuth.jfr.enabled=true`, instrumentation appends a bounded JFR startup argument to Maven's session `argLine` property and stores one raw recording per Surefire or Failsafe test JVM in `target/testsleuth/jfr/`. The console and HTML report summary print the number and total size of recordings; the console also reports parse status and counts for selected JFR event families. JUnit 4 and JUnit 5 listeners emit TestSleuth lifecycle spans containing canonical test identity, framework, outcome, thread, and JFR timing into those recordings. JUnit 5 also emits separate `before-each` and `after-each` spans, preserving setup and teardown timing independently from the test body. TestSleuth attributes same-thread wait, lock, socket, and file events inside a setup or teardown span to that phase first; only remaining events inside a test span are direct test-body evidence. This prevents repeated fixture cost from being reported as test-body work. JUnit 5 collector state is safe for concurrent callbacks, and attribution requires the same JFR recording as well as the same thread and time window, preventing cross-fork ownership. Candidate events that cannot be directly owned are reported separately: one active test on another thread is asynchronous evidence, multiple active tests is shared-concurrent evidence, and no active test is unclassified. These categories are never charged to a test.

For directly owned JFR evidence, console output emits up to five ranked, duration-bearing cause explanations. The initial rules cover fixed sleeps, parked or polling waits, socket I/O, monitor contention, file I/O, and same-thread class loading/warm-up. Every explanation includes the measured event duration, evidence location (test body or lifecycle phase), and a targeted remediation direction. When the JFR event contains a user-code frame, the explanation also includes that `class.method:line` stack location. Up to two JFR execution or allocation samples are emitted separately as `JFR signal` lines after the measured causes, so they remain visible in wait-heavy runs. Signals identify direct same-thread activity and a stack location, but deliberately do not claim elapsed time or a recoverable duration. Garbage-collection pause time is reported only as shared JVM evidence when its collection interval overlaps active tests; it is never charged to one test. Framework-specific explanations remain future work.

## Sample Project

The repository includes an intentionally slow Maven/JUnit 5 sample:

```bash
mvn -pl testsleuth-samples/slow-junit5-maven verify
```

The sample produces default slow-test findings without lowering the `1000ms` threshold,
and includes fixed-wait, polling-wait, JDK timed wait, setup-heavy, Failsafe integration-test,
legacy JUnit 4, and Spring-style framework initialization examples.

It also includes an opt-in worst-case sample that deliberately exercises slow-test,
fixed-wait source, polling-wait source, framework-initialization, and runtime-wait
findings together:

```bash
mvn -pl testsleuth-samples/slow-junit5-maven verify \
  -Dtestsleuth.sample.worstCase=true \
  -Dtestsleuth.detectors.fixedWaits=true \
  -Dtestsleuth.detectors.pollingWaits=true \
  -Dtestsleuth.detectors.frameworkInitialization=true \
  -Dtestsleuth.runtime.waits=true \
  -Dtestsleuth.runtime.waitStacks=true \
  -Dtestsleuth.console.detail=findings
```

Capture raw JFR evidence for the sample's Surefire and Failsafe JVMs:

```bash
mvn -pl testsleuth-samples/slow-junit5-maven -am verify \
  -Dtestsleuth.jfr.enabled=true \
  -Dtestsleuth.console.detail=findings
```

The repository also includes a pure Maven/JUnit 4 sample that exercises the legacy Surefire JUnit 4 provider and TestSleuth's JUnit 4 `RunListener` path:

```bash
mvn -pl testsleuth-samples/slow-junit4-maven verify
```

This sample writes `target/testsleuth/junit4-events.json` and merges those lifecycle events into `target/testsleuth/events.json`.

### Maven Report Configuration

The report goal is configurable with Maven properties:

```bash
mvn testsleuth:instrument test testsleuth:report \
  -Dtestsleuth.threshold.slowTestMillis=1000 \
  -Dtestsleuth.threshold.verySlowTestMillis=5000 \
  -Dtestsleuth.threshold.fixedWaitMillis=250 \
  -Dtestsleuth.threshold.pollingWaitMillis=100 \
  -Dtestsleuth.findings.max=10 \
  -Dtestsleuth.detectors.fixedWaits=false \
  -Dtestsleuth.detectors.pollingWaits=false \
  -Dtestsleuth.detectors.frameworkInitialization=false \
  -Dtestsleuth.runtime.waits=false \
  -Dtestsleuth.runtime.waitStacks=false \
  -Dtestsleuth.jfr.enabled=false \
  -Dtestsleuth.console.detail=summary
```

Current options:

- `testsleuth.console.enabled`: `true` by default.
- `testsleuth.console.detail`: `quiet`, `summary`, or `findings`; default is `summary`. `findings` adds one compact log line per finding with module, fork, and collector context when available.
- `testsleuth.threshold.slowTestMillis`: minimum duration for slow-test findings; default is `1000`.
- `testsleuth.threshold.verySlowTestMillis`: threshold for high-severity slow-test findings; default is `5000`.
- `testsleuth.threshold.fixedWaitMillis`: minimum direct `Thread.sleep(...)` or JDK timed wait duration for fixed-wait source findings; default is `250`.
- `testsleuth.threshold.pollingWaitMillis`: minimum direct `Thread.sleep(...)` or JDK timed wait duration inside a nearby loop for polling-wait source findings; default is `100`.
- `testsleuth.findings.max`: maximum timing findings to show; default is `10`.
- `testsleuth.detectors.slowTests`: enables the current slow-test detector; default is `true`.
- `testsleuth.detectors.fixedWaits`: enables opt-in test-source scanning for direct `Thread.sleep(...)` calls and JDK timed waits such as `await(timeout, TimeUnit...)`, `tryAcquire(timeout, TimeUnit...)`, `Future.get(timeout, TimeUnit...)`, and `orTimeout(timeout, TimeUnit...)`; default is `false`.
- `testsleuth.detectors.pollingWaits`: enables opt-in test-source scanning for direct `Thread.sleep(...)` calls and JDK timed waits inside nearby loops; default is `false`.
- `testsleuth.detectors.frameworkInitialization`: enables opt-in source and timing correlation for framework/application-context initialization candidates; default is `false`.
- `testsleuth.runtime.waits`: enables opt-in runtime wait collector classpath and event-file wiring for tests that use the direct runtime wait collector API; default is `false`.
- `testsleuth.runtime.waitStacks`: enables opt-in runtime wait stack evidence for supported runtime collectors; default is `false`.
- `testsleuth.jfr.enabled`: enables opt-in, bounded raw JFR capture for each Maven Surefire or Failsafe test JVM. Recordings are written to `target/testsleuth/jfr/`; default is `false`. Java 17+ is required. TestSleuth parses selected events, attributes same-thread duration-bearing events to JUnit 5 test or phase windows, and separately reports direct CPU/allocation samples without treating samples as elapsed time.

## Current JUnit 5 Listener

The `testsleuth-junit5` module provides a JUnit Platform `TestExecutionListener` and a JUnit Jupiter extension. When it is present on the test runtime classpath and the `testsleuth.junit.events.file` system property is set, it writes JUnit lifecycle events to that JSON file. The Maven instrumentation goal enables listener and Jupiter extension autodetection so per-test setup and teardown phase events are captured when JUnit Jupiter is present.

The `testsleuth-junit4` module provides a JUnit 4 `RunListener` for legacy Surefire/Failsafe JUnit 4 provider runs. When configured, this listener writes JUnit 4 lifecycle events to `target/testsleuth/junit4-events.json`, which is merged into the normal `events.json` report output. Mixed JUnit 4/JUnit 5 suites that run JUnit 4 tests through the JUnit Vintage engine are still visible through Maven XML and JUnit Platform events.

Pure legacy JUnit 4 provider runs also require Surefire/Failsafe `properties.listener` configuration because that provider setting is not exposed as a Maven user property. The pure JUnit 4 sample shows the required static configuration while TestSleuth supplies the listener dependency and event output property.

The Maven `testsleuth:instrument` goal adds the listener dependencies and sets the required JUnit Platform and TestSleuth event-output properties for the current Maven session.

## License

TestSleuth is licensed under the Apache License, Version 2.0. See [LICENSE.txt](LICENSE.txt) and [NOTICE](NOTICE).

## Repository Layout

```text
testsleuth-core
testsleuth-junit4
testsleuth-junit5
testsleuth-runtime-wait
testsleuth-report
testsleuth-maven-plugin
testsleuth-samples/slow-junit5-maven
testsleuth-samples/slow-junit4-maven
docs
```

Additional Maven collectors, detector modules, benchmarks, samples, and CLI modules will be added as the roadmap phases become active. Gradle support is intentionally deferred for now.

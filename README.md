# TestSleuth

TestSleuth is a local-first test-performance diagnosis and optimization profiler.

The product direction is runtime-first: TestSleuth combines test and build lifecycle facts, runtime measurements, and source or configuration context to identify meaningful costs and produce ranked, evidence-backed actions to reduce suite duration.

The current implementation is Maven-first. It uses Maven reports and JUnit lifecycle data to identify affected tests, runtime collectors to observe behavior during the run, and source analysis to enrich likely causes and remediation. Java Flight Recorder (JFR) is the current optional deep-runtime measurement backend; it is one input to TestSleuth's normalized evidence and finding model, not the product boundary. Future measurement backends can produce the same TestSleuth data and reports.

## Current Scope

- Java 17+ codebase.
- Maven reactor as the initial build foundation.
- Core event and finding model.
- Basic report rendering module.
- Maven plugin goals for instrumentation, reporting, and aggregation.
- Architecture decision records under `docs/adr`, including the runtime wait event direction and runtime-first JFR pivot.
- TestSleuth diagnostic data that combines Maven reports, JUnit lifecycle spans, optional runtime measurements, and source or configuration evidence.
- Findings labeled by evidence type (`MEASURED`, `CORRELATED`, `INFERRED`, or `POTENTIAL`) and attribution scope, independent of the measurement backend that supplied the evidence.

## Build

```bash
mvn verify
```

## Architecture

TestSleuth separates measurement from diagnosis.

```text
Test/build measurements + source/configuration context
    -> Normalized TestSleuth events
    -> Lifecycle and runtime attribution
    -> Cause classification and repetition analysis
    -> Findings
    -> Report
    -> Verification
```

Measurement sources emit normalized facts. Detectors consume those facts later and produce evidence-backed TestSleuth findings. This keeps source-specific instrumentation separate from the shared event model, allows multiple measurement backends to contribute to one report, and makes detector behavior testable without rerunning a user's suite.

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

Gradle plugin support remains a product roadmap item, but current implementation work is intentionally focused on the Maven diagnostic path. JFR is the current deep-runtime backend on that path.

## Current Maven Report Output

The current `testsleuth:report` goal is an early Maven measurement foundation. It scans Maven Surefire and Failsafe XML reports, merges JUnit lifecycle events when available, writes `target/testsleuth/events.json`, writes machine-readable `target/testsleuth/findings.json`, and writes a basic `target/testsleuth/index.html`.

To enable the current JUnit listener before tests run:

```bash
mvn testsleuth:instrument test testsleuth:report
```

Projects can also bind the plugin into the Maven lifecycle so normal `mvn verify` runs instrumentation before tests and writes the report during `verify`.

The report includes timing-based findings for the slowest tests observed in Maven test reports, plus runtime-backed cause findings when runtime measurement is enabled. Findings include joined JUnit/Maven evidence and run context such as module, build run, Maven project, process, and fork when available. `findings.json` contains the same TestSleuth finding payload for CI systems and other tooling. `findings.json` and the HTML report identify each finding's evidence type and attribution scope so directly observed facts, corroborated explanations, and source-only patterns remain distinct.

When runtime wait collection is enabled and runtime wait events are present, reports also produce measured fork-wide wait findings above the configured fixed-wait threshold. Source wait and source/timing framework detectors remain separate and opt-in; they are labeled `POTENTIAL` with no measured or recoverable cost until runtime evidence corroborates them.

Console and HTML summaries also include an initial timing reconciliation: Maven-reported test time, JUnit-observed test time, JUnit setup and teardown time, and unclassified observed Maven lifecycle time when available. Console output includes named timing buckets for the lifecycle window, Maven test execution, JUnit observed execution, setup, teardown, unclassified lifecycle time, and report generation. When runtime wait events are present, console output also reports runtime wait event count, observed wait time, and collector overhead. The unclassified lifecycle bucket is a coarse signal for build setup, compilation, runner overhead, framework initialization, or other work not yet attributed to a specific test phase. The HTML report includes a run summary scorecard, top opportunity, and category breakdown before the detailed findings table.

### Current Deep-Runtime Measurement Backend

When `testsleuth.jfr.enabled=true`, TestSleuth uses JFR as its current deep-runtime measurement backend. Instrumentation appends a bounded recording startup argument to Maven's session `argLine` property and stores one raw recording per Surefire or Failsafe test JVM in `target/testsleuth/jfr/`. The console and HTML report summary print the number and total size of recordings; the console also reports parse status and counts for selected runtime event families. JUnit 4 and JUnit 5 listeners emit TestSleuth lifecycle spans containing canonical test identity, framework, outcome, and thread into those recordings. JUnit 5 also emits separate `before-each` and `after-each` spans, preserving setup and teardown timing independently from the test body. TestSleuth then attributes same-thread wait, lock, socket, and file events inside a setup or teardown span to that phase first; only remaining events inside a test span are direct test-body evidence. This prevents repeated fixture cost from being reported as test-body work. JUnit 5 collector state is safe for concurrent callbacks, and attribution requires the same recording as well as the same thread and time window, preventing cross-fork ownership. Candidate events that cannot be directly owned are reported separately: one active test on another thread is asynchronous evidence, multiple active tests is shared-concurrent evidence, and no active test is unclassified. These categories are never charged to a test.

For directly owned runtime evidence from this backend, console output emits up to five ranked, duration-bearing cause explanations. The initial rules cover fixed sleeps, parked or polling waits, socket I/O, monitor contention, file I/O, and same-thread class loading or warm-up. Every explanation includes the measured event duration, evidence location (test body or lifecycle phase), and a targeted remediation direction. When the runtime event contains a user-code frame, the explanation also includes that `class.method:line` stack location. Up to two execution or allocation samples are emitted separately as `JFR signal` lines after the measured causes, so they remain visible in wait-heavy runs. Signals identify direct same-thread activity and a stack location, but deliberately do not claim elapsed time or a recoverable duration. Garbage-collection pause time is reported only as shared JVM evidence when its collection interval overlaps active tests; it is never charged to one test.

Runtime evidence is emitted as normal TestSleuth findings in `findings.json` and `index.html`. Direct duration causes are `MEASURED`; direct CPU/allocation samples and GC evidence are `CORRELATED`, preserving their scope and avoiding a false elapsed-time claim for samples. Direct duration events with the same user-code stack are additionally grouped across at least two tests into a measured repeated-operation finding, showing the total observed duration and the individual affected tests. This roll-up is not extra time beyond the individual component findings. When the opt-in framework-initialization detector finds a framework indicator in source and direct runtime duration at that class's user-code stack, it emits one `INFERRED` framework-initialization finding with the measured observed cost and suppresses that class's duplicate source-only candidate. The elapsed time is measured, while the framework semantic label remains inferred until dedicated framework spans are available. These findings can overlap existing slow-test findings, because the latter identify which test was slow while the runtime evidence explains a possible cause. Console finding counts report all findings, including runtime evidence below the slow-test threshold.

## How to Read TestSleuth Diagnostic Data

Run a detailed diagnostic locally or in CI with:

```bash
mvn verify \
  -Dtestsleuth.jfr.enabled=true \
  -Dtestsleuth.detectors.frameworkInitialization=true \
  -Dtestsleuth.console.detail=findings
```

Start with the detailed `[TestSleuth] - ...` findings, then use the evidence type and scope to determine how reliable and actionable each finding is. Some current finding labels include `JFR` to disclose that the current JFR backend supplied the underlying runtime fact. They are TestSleuth findings, and their evidence model and report schema do not depend on JFR.

| TestSleuth data | Meaning | What to do |
| --- | --- | --- |
| `Slow observed test` | Maven and JUnit agree that a test is slow. It identifies the symptom, not necessarily the cause. | Use its matching runtime cause or repeated-operation finding to choose a fix. |
| `JFR fixed wait`, `external I/O`, `lock contention`, or `file I/O` | A duration-bearing runtime event occurred on the test thread or its lifecycle phase. `JFR` is the current measurement-source label on applicable findings. | Treat this as direct evidence. Inspect the stack and apply the reported action. |
| `Repeated JFR ...` | The same direct event family and user-code stack occurred in at least two tests. It is a TestSleuth repeated-operation finding; `JFR` currently identifies its measurement source. Its duration is a roll-up of component findings, not additional suite time. | Prioritize shared setup, boundaries, fixtures, or infrastructure that can remove the repeated operation. |
| `Runtime-backed framework initialization` | Direct runtime duration occurred at a source class with a framework/context indicator. The duration is measured; the semantic framework label is inferred. | Check context reuse, test slicing, and configuration differences. |
| `JFR phase evidence` or `scope=FRAMEWORK_OR_FIXTURE` | Cost belongs to JUnit setup or teardown rather than the test body. `JFR` is the current source label when this phase evidence comes from the deep-runtime backend. | Improve fixture scope or lifecycle before changing assertions or application behavior. |
| `JFR signal` | CPU or allocation samples show activity, not elapsed time. The label identifies the current measurement source. | Use the sampled stack to investigate; do not claim the sample count is recoverable milliseconds. |
| `shared JVM`, `shared-concurrent`, `asynchronous`, or `unclassified` evidence | TestSleuth observed work but cannot safely charge it to one test. | Investigate it at suite, fork, executor, or JVM scope. Do not blame the active test. |
| `POTENTIAL` finding | Source/configuration pattern was found but was not proven to execute in this run. | Treat it as a follow-up lead, not as measured waste. |

### Evidence and Scope

`evidence=MEASURED` means the listed duration was directly observed. `evidence=INFERRED` combines direct runtime facts with source or configuration context. `evidence=CORRELATED` is related runtime evidence that cannot support the same ownership or duration claim. `evidence=POTENTIAL` is static-only.

`scope=DIRECT_TEST_THREAD` is safe to associate with that test. `scope=FRAMEWORK_OR_FIXTURE` is setup or teardown work. `scope=SHARED_JVM`, `SHARED_CONCURRENT`, `CORRELATED_ASYNCHRONOUS`, and `UNCLASSIFIED` must remain separate from per-test totals.

For example:

```text
[TestSleuth] - MEDIUM Repeated JFR fixed wait:
...SimulatedApplicationContextCache.load:36
(1310 ms, evidence=MEASURED, scope=DIRECT_TEST_THREAD)

[TestSleuth] - MEDIUM Runtime-backed framework initialization:
...FrameworkInitializationSampleTest
(1310 ms, evidence=INFERRED, scope=FRAMEWORK_OR_FIXTURE)
```

These lines describe the same underlying operation from two useful TestSleuth perspectives: one proves it repeats; the other explains the likely framework/context cause. Do not add `1310 ms + 1310 ms`.

### AI-Assisted Triage

Use `target/testsleuth/findings.json` as the primary input because it contains full evidence, affected subjects, actions, trade-offs, and verification steps. Include the `[TestSleuth]` console lines for timing and runtime-attribution context. Redact credentials, tokens, private host names, request payloads, and sensitive file paths before sharing artifacts outside the repository.

An effective prompt is:

```text
You are reviewing TestSleuth diagnostic data from a Maven test run. Use evidence type and attribution scope strictly.
Do not sum repeated-operation, framework, slow-test, or component runtime findings when they describe the same work.
Separate directly measured facts from inferred and potential explanations.
Rank the top three remediation opportunities by suite-level impact, confidence, and safety.
For each, name the affected tests, cite the runtime stack or evidence, propose the smallest safe change,
state trade-offs, and give a verification command or measurable success criterion.

Console output:
<paste [TestSleuth] lines>

findings.json:
<paste redacted findings.json>
```

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

Enable the current JFR deep-runtime measurement backend for the sample's Surefire and Failsafe JVMs:

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
- `testsleuth.jfr.enabled`: enables the opt-in, bounded JFR deep-runtime measurement backend for each Maven Surefire or Failsafe test JVM. Recordings are written to `target/testsleuth/jfr/`; default is `false`. Java 17+ is required. TestSleuth parses selected events, attributes same-thread duration-bearing events to JUnit 5 test or phase windows, and separately reports direct CPU/allocation samples without treating samples as elapsed time.

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

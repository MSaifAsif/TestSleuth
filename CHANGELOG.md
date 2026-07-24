# Changelog

All notable changes to TestSleuth should be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project intends to use semantic versioning once public releases begin.

## [Unreleased]

### Added

- JFR attribution now reports up to two direct same-thread CPU and allocation samples as explicitly non-duration `JFR signal` evidence after ranked measured causes, including the sampled user-code location when available.
- JFR reporting now explains directly attributed class loading as warm-up work and reports overlapping garbage-collection pause time as shared JVM evidence without charging a test.
- Direct JFR cause explanations now include a user-code `class.method:line` location when the recorded runtime event has stack-trace evidence.
- JFR reports now emit up to five ranked, directly measured runtime-cause explanations with targeted actions for fixed waits, parked/polling waits, socket I/O, lock contention, and file I/O.
- JFR console output now reports unowned candidate events as asynchronous, shared-concurrent, or unclassified evidence without charging them to a test.
- JUnit 5 lifecycle collection now supports concurrent callbacks, and JFR event attribution requires the source recording in addition to matching thread and time window, preventing cross-fork ownership.
- JFR reporting now attributes same-thread runtime events in JUnit 5 `before-each` and `after-each` spans to separate setup/teardown evidence, before falling back to direct test-body attribution.
- JUnit 5 lifecycle instrumentation now emits separate `before-each` and `after-each` JFR spans, preserving setup and teardown timing independently from test-body spans.
- Opt-in Maven JFR recording bootstrap with `testsleuth.jfr.enabled=true`, bounded per-test-JVM recordings under `target/testsleuth/jfr/`, composed Maven `argLine` support, and console/HTML recording count and size reporting.
- Suppressed JFR startup logging in Maven test forks so Surefire and Failsafe do not report JFR startup output as a corrupted fork channel.
- Maven reports now safely parse captured JFR files and report recording validity plus selected fork-wide event-family counts without assigning those events to tests.
- JUnit 4 and JUnit 5 lifecycle collectors now emit custom `dev.testsleuth.TestLifecycle` JFR spans with test identity, framework, outcome, thread, and duration for future runtime attribution.
- Maven JFR reports now perform conservative same-thread, in-test-window attribution for waits, locks, socket, and file events, and identify the highest directly measured test evidence without assigning shared JVM work to a test.
- Finding schema fields for evidence type and attribution scope, surfaced in JSON, HTML, and detailed Maven console output.
- Version 3 roadmap alignment: source-only wait and framework patterns are now explicitly reported as potential/static evidence, while observed timing and runtime waits retain measured evidence labels.
- `testsleuth-junit5` module with a JUnit Platform listener that records discovered, started, and finished test events.
- `testsleuth-junit4` module with a JUnit 4 RunListener that records started and finished test events.
- Maven `testsleuth:instrument` goal that prepares JUnit listener auto-detection before tests run.
- Maven `testsleuth:report` now merges JUnit lifecycle events with Maven XML report events when both are present.
- Core runtime wait event attribute contract for future opt-in runtime wait instrumentation.
- Runtime wait collector implementation plan documenting the next opt-in collector slices.
- Runtime wait collector module shell with normalized wait observation event mapping and overhead attributes.
- Runtime wait collector API that records normalized events for direct `Thread.sleep`, `Object.wait`, `LockSupport.parkNanos`, and `LockSupport.parkUntil` waits.
- Maven instrumentation can now opt in runtime wait collector classpath and event-file wiring with `testsleuth.runtime.waits=true`.
- Maven reports now emit runtime wait findings for observed runtime wait events above the configured fixed-wait threshold.
- Maven console output now reports runtime wait event count, observed wait time, and runtime collector overhead when runtime wait events are present.
- Maven configuration tests now cover all current TestSleuth options enabled together.
- Slow Maven/JUnit 5 sample now includes an opt-in worst-case test that exercises slow-test, fixed-wait, polling-wait, framework-initialization, and runtime-wait findings together.
- First timing-based Maven detector that reports the slowest observed tests as investigation findings.
- Configurable Maven report thresholds, finding limits, detector flags, and console detail.
- Concise CI-oriented console summary from `testsleuth:report`.
- Intentionally slow Maven/JUnit 5 sample project for validating default findings.
- Intentionally slow pure Maven/JUnit 4 sample project for validating legacy RunListener integration.
- Additional sample scenarios for fixed waits, polling-style delays, and setup-heavy tests.
- Maven `testsleuth:aggregate-report` goal for root-level aggregation across reactor modules.
- Canonical test identities shared by JUnit lifecycle events and Maven XML report events.
- Build-run, module, Maven project, process, and fork context attributes on generated events.
- Core detector API with a reusable slow-test detector used by the Maven plugin.
- Identity-aware observation joining across JUnit lifecycle and Maven XML report events.
- Slow-test findings now include joined module, build-run, Maven project, process, and fork context.
- Finding-level console output now includes compact module, fork, and collector context.
- Opt-in fixed-wait source detector for direct `Thread.sleep(...)` calls in Maven test sources.
- Opt-in source wait detectors now recognize common JDK timed waits such as latch waits, semaphore waits, future timeouts, and `CompletableFuture` timeouts.
- Maven report goals now write machine-readable `findings.json` output.
- Slow Maven/JUnit 5 sample now binds TestSleuth into the normal Maven lifecycle.
- Maven detectors now run against combined JUnit lifecycle and Maven XML events when both are available.
- Aggregate reports now include Maven-scanned fallback events for modules without module-level `events.json`.
- Root `AGENTS.md` Codex context document for future agent sessions.
- ADR 0004 documenting the normalized runtime wait event direction before Java agent implementation.
- ADR 0005 documenting the Version 2 runtime-first JFR architecture pivot.
- Maven XML report events now include Surefire/Failsafe runner metadata and configured fork settings when available.
- Maven reports now include an observed lifecycle window from `testsleuth:instrument` to `testsleuth:report`.
- Maven console and HTML summaries now include initial timing reconciliation for Maven-reported test time, JUnit-observed test time, setup time, teardown time, and lifecycle remainder.
- Maven timing reconciliation now exposes named console timing buckets and labels unattributed time as unclassified lifecycle time.
- JUnit 5 instrumentation now captures per-test setup and teardown phase events through a Jupiter extension.
- Maven instrumentation now injects JUnit 4 listener support and merges `junit4-events.json` into normal reports.
- JUnit lifecycle event collectors now preserve existing event files so Surefire and Failsafe phases can contribute to the same report run.
- Maven instrumentation now resets per-run TestSleuth event and finding files before tests start to avoid stale report data.
- HTML reports now include a run summary scorecard, top opportunity, and category breakdown.
- Maven console output now reports TestSleuth report overhead.
- Slow Maven/JUnit 5 sample now includes Spring-style framework initialization scenarios without external Spring dependencies.
- Slow Maven/JUnit 5 sample now includes a Failsafe integration-test scenario to validate Surefire and Failsafe report scanning in one lifecycle run.
- Opt-in polling-wait source detector for direct `Thread.sleep(...)` calls inside loops.
- Opt-in framework-initialization candidate detector that correlates application-context source indicators with observed class duration.
- Maven report goal now scans Surefire and Failsafe XML reports and writes `events.json`.
- Initial Maven reactor repository foundation.
- Core event and finding model stubs.
- Dependency-free JSON export for TestSleuth events.
- Basic HTML report renderer.
- Maven plugin shell with a `testsleuth:report` goal.
- Project documentation, ADRs, and GitHub build workflow.

### Changed

- Consolidated architecture documentation into the root `README.md` and removed the standalone implementation tracker.

### Fixed

- JFR attribution now skips an individual unsupported event schema instead of discarding the remaining valid lifecycle and runtime events in that recording.
- Maven JUnit 4 listener injection now preserves existing Surefire/Failsafe listeners and avoids duplicate TestSleuth listener entries.
- Maven instrumentation logs now describe both TestSleuth JUnit listener modules instead of only JUnit 5.

### Security

- None yet.

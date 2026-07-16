# Changelog

All notable changes to TestSleuth should be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project intends to use semantic versioning once public releases begin.

## [Unreleased]

### Added

- `testsleuth-junit5` module with a JUnit Platform listener that records discovered, started, and finished test events.
- `testsleuth-junit4` module with a JUnit 4 RunListener that records started and finished test events.
- Maven `testsleuth:instrument` goal that prepares JUnit listener auto-detection before tests run.
- Maven `testsleuth:report` now merges JUnit lifecycle events with Maven XML report events when both are present.
- Core runtime wait event attribute contract for future opt-in runtime wait instrumentation.
- Maven runtime wait collection flags reserved for future opt-in wait instrumentation.
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
- Maven XML report events now include Surefire/Failsafe runner metadata and configured fork settings when available.
- Maven reports now include an observed lifecycle window from `testsleuth:instrument` to `testsleuth:report`.
- Maven console and HTML summaries now include initial timing reconciliation for Maven-reported test time, JUnit-observed test time, setup time, teardown time, and lifecycle remainder.
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

- Maven JUnit 4 listener injection now preserves existing Surefire/Failsafe listeners and avoids duplicate TestSleuth listener entries.
- Maven instrumentation logs now describe both TestSleuth JUnit listener modules instead of only JUnit 5.

### Security

- None yet.

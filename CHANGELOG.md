# Changelog

All notable changes to TestSleuth should be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project intends to use semantic versioning once public releases begin.

## [Unreleased]

### Added

- `testsleuth-junit5` module with a JUnit Platform listener that records discovered, started, and finished test events.
- Maven `testsleuth:instrument` goal that prepares JUnit listener auto-detection before tests run.
- Maven `testsleuth:report` now merges JUnit lifecycle events with Maven XML report events when both are present.
- First timing-based Maven detector that reports the slowest observed tests as investigation findings.
- Configurable Maven report thresholds, finding limits, detector flags, and console detail.
- Concise CI-oriented console summary from `testsleuth:report`.
- Intentionally slow Maven/JUnit 5 sample project for validating default findings.
- Additional sample scenarios for fixed waits, polling-style delays, and setup-heavy tests.
- Maven `testsleuth:aggregate-report` goal for root-level aggregation across reactor modules.
- Canonical test identities shared by JUnit lifecycle events and Maven XML report events.
- Build-run, module, Maven project, process, and fork context attributes on generated events.
- Core detector API with a reusable slow-test detector used by the Maven plugin.
- Identity-aware observation joining across JUnit lifecycle and Maven XML report events.
- Slow-test findings now include joined module, build-run, Maven project, process, and fork context.
- Finding-level console output now includes compact module, fork, and collector context.
- Maven report goal now scans Surefire and Failsafe XML reports and writes `events.json`.
- Initial Maven reactor repository foundation.
- Core event and finding model stubs.
- Dependency-free JSON export for TestSleuth events.
- Basic HTML report renderer.
- Maven plugin shell with a `testsleuth:report` goal.
- Project documentation, ADRs, and GitHub build workflow.

### Changed

- None yet.

### Fixed

- None yet.

### Security

- None yet.

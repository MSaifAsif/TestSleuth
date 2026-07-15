# Implementation Start

The first implementation milestone is a buildable repository foundation that reflects the roadmap without overcommitting to unbuilt behavior.

## Done

- Maven reactor.
- Java 17 release target.
- Core event model package.
- Core finding model package.
- JSON event export.
- JUnit Platform listener for discovered, started, and finished test events.
- Basic HTML report renderer.
- Maven plugin shell with a `testsleuth:report` goal.
- Maven plugin `testsleuth:instrument` goal for JUnit listener setup.
- Surefire and Failsafe XML report scanning for initial event generation.
- Merged report event output from JUnit lifecycle files and Maven XML reports.
- First timing-based Maven detector for slow observed tests.
- Configurable slow-test thresholds, finding limits, detector flags, and console verbosity.
- CI-oriented console summary from the Maven report goal.
- Intentionally slow Maven/JUnit 5 sample project for validating default findings.
- Additional sample scenarios for fixed waits, polling-style delays, and setup-heavy tests.
- Maven `testsleuth:aggregate-report` goal for root-level aggregation across reactor modules.
- Canonical test identities shared by JUnit lifecycle events and Maven XML report events.
- Build-run, module, Maven project, process, and fork context attributes on generated events.
- Core detector API with a reusable slow-test detector used by the Maven plugin.
- Identity-aware observation joining across JUnit lifecycle and Maven XML report events.
- Slow-test finding evidence includes joined module, build-run, Maven project, process, and fork context.
- Finding-level console output includes compact module, fork, and collector context for CI logs.
- ADRs for repository foundation, measurement separation, and local-first behavior.

## Next

1. Capture richer worker/fork details where each test runner exposes them.
2. Add more detectors behind explicit configuration flags.
3. Add Spring/framework initialization sample coverage.
4. Improve aggregate-report lifecycle ergonomics for default Maven usage.

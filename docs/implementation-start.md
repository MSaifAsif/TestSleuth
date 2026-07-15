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
- ADRs for repository foundation, measurement separation, and local-first behavior.

## Next

1. Extend the event schema with build-run, module, worker, fork, and process identifiers.
2. Reconcile collector events with Maven XML report events.
3. Add stable build-run, module, worker, fork, and process identifiers.
4. Start detector APIs outside the Maven plugin module.
5. Add more benchmark scenarios for setup, waits, and framework initialization.

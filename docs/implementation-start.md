# Implementation Start

The first implementation milestone is a buildable repository foundation that reflects the roadmap without overcommitting to unbuilt behavior.

## Done

- Maven reactor.
- Java 17 release target.
- Core event model package.
- Core finding model package.
- Basic HTML report renderer.
- Maven plugin shell with a `testsleuth:report` goal.
- ADRs for repository foundation, measurement separation, and local-first behavior.
- GitHub Actions build workflow.

## Next

1. Extend the event schema with build-run, module, worker, fork, and process identifiers.
2. Define JSON serialization for event export.
3. Add a `testsleuth-junit5` module for lifecycle collection.
4. Make the Maven plugin inject the collector into Surefire and Failsafe.
5. Add intentionally slow sample projects under a benchmark or samples module.


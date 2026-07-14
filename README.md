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

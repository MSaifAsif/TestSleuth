# Slow JUnit 5 Maven Sample

This sample contains intentionally slow tests for validating TestSleuth behavior.
It covers direct slow tests, fixed waits, repeated polling-style delays, and setup-heavy tests.

Run the sample with TestSleuth:

```bash
mvn -pl testsleuth-samples/slow-junit5-maven \
  dev.testsleuth:testsleuth-maven-plugin:0.1.0-SNAPSHOT:instrument \
  test \
  dev.testsleuth:testsleuth-maven-plugin:0.1.0-SNAPSHOT:report
```

The default slow-test threshold is `1000ms`, so `slowExternalCallSimulation` and
`fixedSleepWaitingForExternalSignal` should produce visible findings without lowering thresholds.

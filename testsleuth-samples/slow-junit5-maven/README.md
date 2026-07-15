# Slow JUnit 5 Maven Sample

This sample contains intentionally slow tests for validating TestSleuth behavior.
It covers direct slow tests, fixed waits, repeated polling-style delays, setup-heavy tests,
and Spring-style framework initialization patterns without requiring Spring dependencies.

Run the sample with TestSleuth:

```bash
mvn -pl testsleuth-samples/slow-junit5-maven verify
```

The default slow-test threshold is `1000ms`, so `slowExternalCallSimulation` and
`fixedSleepWaitingForExternalSignal` should produce visible findings without lowering thresholds.

Enable source scanning for direct fixed waits and repeated polling-style delays with:

```bash
mvn -pl testsleuth-samples/slow-junit5-maven verify \
  -Dtestsleuth.detectors.fixedWaits=true \
  -Dtestsleuth.detectors.pollingWaits=true \
  -Dtestsleuth.console.detail=findings
```

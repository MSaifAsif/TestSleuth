# Slow JUnit 5 Maven Sample

This sample contains intentionally slow tests for validating TestSleuth behavior.

Run the sample with TestSleuth:

```bash
mvn -pl testsleuth-samples/slow-junit5-maven \
  dev.testsleuth:testsleuth-maven-plugin:0.1.0-SNAPSHOT:instrument \
  test \
  dev.testsleuth:testsleuth-maven-plugin:0.1.0-SNAPSHOT:report
```

The default slow-test threshold is `1000ms`, so the `slowExternalCallSimulation` test should produce a visible finding without lowering thresholds.


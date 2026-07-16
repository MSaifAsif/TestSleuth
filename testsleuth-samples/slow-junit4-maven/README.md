# Slow JUnit 4 Maven Sample

This sample validates TestSleuth against a pure JUnit 4 Maven project. It intentionally avoids JUnit Jupiter and the JUnit Vintage engine so Maven Surefire uses the legacy JUnit 4 provider path.

The sample declares the Surefire `listener` property statically because Surefire's legacy JUnit 4 provider does not expose that provider property as a Maven `-D...` user property. TestSleuth still contributes the listener dependency and event-file system property during `testsleuth:instrument`.

Run it with:

```bash
mvn -pl testsleuth-samples/slow-junit4-maven verify
```

Useful validation signals:

- `target/testsleuth/junit4-events.json` exists.
- `target/testsleuth/events.json` includes `collector=junit4-listener`.
- Console output reports the slow legacy test as a TestSleuth finding.

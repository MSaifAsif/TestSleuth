package dev.testsleuth.maven;

import dev.testsleuth.core.event.TestSleuthEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MavenTestReportScannerTest {
    @TempDir
    private Path tempDir;

    @Test
    void scansSurefireXmlReportsIntoEvents() throws IOException {
        Path reports = tempDir.resolve("surefire-reports");
        Files.createDirectories(reports);
        Files.writeString(reports.resolve("TEST-dev.testsleuth.ExampleTest.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite tests="2" failures="1" errors="0" skipped="0">
                  <testcase classname="dev.testsleuth.ExampleTest" name="passes" time="0.012"/>
                  <testcase classname="dev.testsleuth.ExampleTest" name="fails" time="1.250">
                    <failure message="boom"/>
                  </testcase>
                </testsuite>
                """);

        MavenTestReportScanner.ScanResult result = new MavenTestReportScanner().scan(List.of(reports));

        assertEquals(2, result.testCount());
        TestSleuthEvent passed = result.events().get(0);
        TestSleuthEvent failed = result.events().get(1);
        assertEquals("dev.testsleuth.ExampleTest.passes", passed.subject().identifier());
        assertEquals("dev.testsleuth.ExampleTest.passes", passed.attributes().get("testIdentity"));
        assertEquals("passes", passed.attributes().get("methodName"));
        assertEquals("passed", passed.attributes().get("status"));
        assertEquals("12", passed.attributes().get("durationMillis"));
        assertEquals("failed", failed.attributes().get("status"));
        assertEquals("1250", failed.attributes().get("durationMillis"));
    }
}

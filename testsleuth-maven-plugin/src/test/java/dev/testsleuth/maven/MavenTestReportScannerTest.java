package dev.testsleuth.maven;

import dev.testsleuth.core.event.TestSleuthRunContext;
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

        MavenTestReportScanner.ScanResult result = new MavenTestReportScanner(runContext()).scan(List.of(reports));

        assertEquals(2, result.testCount());
        TestSleuthEvent passed = result.events().get(0);
        TestSleuthEvent failed = result.events().get(1);
        assertEquals("dev.testsleuth.ExampleTest.passes", passed.subject().identifier());
        assertEquals("dev.testsleuth.ExampleTest.passes", passed.attributes().get("testIdentity"));
        assertEquals("passes", passed.attributes().get("methodName"));
        assertEquals("run-1", passed.attributes().get("buildRunId"));
        assertEquals("dev.testsleuth:sample", passed.attributes().get("moduleId"));
        assertEquals("passed", passed.attributes().get("status"));
        assertEquals("12", passed.attributes().get("durationMillis"));
        assertEquals("failed", failed.attributes().get("status"));
        assertEquals("1250", failed.attributes().get("durationMillis"));
    }

    @Test
    void includesReportDirectoryRunnerAttributes() throws IOException {
        Path reports = tempDir.resolve("failsafe-reports");
        Files.createDirectories(reports);
        Files.writeString(reports.resolve("TEST-dev.testsleuth.IntegrationTest.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite tests="1" failures="0" errors="0" skipped="0">
                  <testcase classname="dev.testsleuth.IntegrationTest" name="passes" time="0.250"/>
                </testsuite>
                """);

        MavenTestReportScanner.ScanResult result = new MavenTestReportScanner(runContext())
                .scanReportDirectories(List.of(new MavenTestReportScanner.ReportDirectory(
                        reports,
                        java.util.Map.of(
                                "testRunner", "failsafe",
                                "testPlugin.forkCount", "2",
                                "testPlugin.reuseForks", "false"
                        )
                )));

        TestSleuthEvent event = result.events().get(0);
        assertEquals("failsafe", event.attributes().get("testRunner"));
        assertEquals("2", event.attributes().get("testPlugin.forkCount"));
        assertEquals("false", event.attributes().get("testPlugin.reuseForks"));
        assertEquals(reports.toString(), event.attributes().get("reportDirectory"));
    }

    @Test
    void scansSurefireAndFailsafeReportDirectoriesTogether() throws IOException {
        Path surefireReports = tempDir.resolve("surefire-reports");
        Path failsafeReports = tempDir.resolve("failsafe-reports");
        Files.createDirectories(surefireReports);
        Files.createDirectories(failsafeReports);
        Files.writeString(surefireReports.resolve("TEST-dev.testsleuth.UnitTest.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite tests="1" failures="0" errors="0" skipped="0">
                  <testcase classname="dev.testsleuth.UnitTest" name="unitPasses" time="0.100"/>
                </testsuite>
                """);
        Files.writeString(failsafeReports.resolve("TEST-dev.testsleuth.IntegrationIT.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite tests="1" failures="0" errors="0" skipped="0">
                  <testcase classname="dev.testsleuth.IntegrationIT" name="integrationPasses" time="0.200"/>
                </testsuite>
                """);

        MavenTestReportScanner.ScanResult result = new MavenTestReportScanner(runContext())
                .scanReportDirectories(List.of(
                        new MavenTestReportScanner.ReportDirectory(
                                surefireReports,
                                java.util.Map.of("testRunner", "surefire")
                        ),
                        new MavenTestReportScanner.ReportDirectory(
                                failsafeReports,
                                java.util.Map.of("testRunner", "failsafe")
                        )
                ));

        assertEquals(2, result.testCount());
        assertEquals("dev.testsleuth.UnitTest.unitPasses", result.events().get(0).attributes().get("testIdentity"));
        assertEquals("surefire", result.events().get(0).attributes().get("testRunner"));
        assertEquals("dev.testsleuth.IntegrationIT.integrationPasses",
                result.events().get(1).attributes().get("testIdentity"));
        assertEquals("failsafe", result.events().get(1).attributes().get("testRunner"));
    }

    private static TestSleuthRunContext runContext() {
        return new TestSleuthRunContext(
                "run-1",
                "dev.testsleuth:sample",
                "dev.testsleuth",
                "sample",
                "0.1.0-SNAPSHOT",
                "/workspace/sample",
                "456",
                "unknown"
        );
    }
}

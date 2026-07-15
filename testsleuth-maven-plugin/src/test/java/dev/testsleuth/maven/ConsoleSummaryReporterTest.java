package dev.testsleuth.maven;

import dev.testsleuth.core.finding.Confidence;
import dev.testsleuth.core.finding.Finding;
import dev.testsleuth.core.finding.FindingCategory;
import dev.testsleuth.core.finding.FindingId;
import dev.testsleuth.core.finding.FindingSeverity;
import dev.testsleuth.core.finding.TimeSavingEstimate;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConsoleSummaryReporterTest {
    @Test
    void findingDetailIncludesCompactContext() {
        RecordingLog log = new RecordingLog();
        TestSleuthMavenConfig config = TestSleuthMavenConfig.from(true, "findings", 1_000, 5_000, 10, true, false, 250);

        new ConsoleSummaryReporter().report(
                log,
                config,
                new MavenTestReportScanner.ScanResult(List.of()),
                3,
                new MavenTimingSummary(
                        java.util.Optional.of(Duration.ofMillis(2_500)),
                        Duration.ofMillis(1_500),
                        Duration.ofMillis(1_450),
                        Duration.ofMillis(100),
                        Duration.ofMillis(50),
                        java.util.Optional.of(Duration.ofMillis(1_000))
                ),
                Duration.ofMillis(75),
                List.of(finding()),
                Path.of("target/testsleuth/index.html"),
                Path.of("target/testsleuth/events.json"),
                Path.of("target/testsleuth/findings.json")
        );

        assertTrue(log.infoLines.stream().anyMatch(line -> line.contains(
                "[TestSleuth] Maven lifecycle window: 2500 ms"
        )));
        assertTrue(log.infoLines.stream().anyMatch(line -> line.contains(
                "[TestSleuth] Timing: Maven tests 1500 ms, JUnit observed 1450 ms, setup 100 ms, teardown 50 ms, lifecycle remainder 1000 ms"
        )));
        assertTrue(log.infoLines.stream().anyMatch(line -> line.contains(
                "[TestSleuth] Report overhead: 75 ms"
        )));
        assertTrue(log.infoLines.stream().anyMatch(line -> line.contains(
                "[TestSleuth] - MEDIUM Slow observed test: slowExample "
                        + "(1500 ms, module=dev.testsleuth:sample, fork=1, runner=surefire, collectors=junit5-listener, maven-test-report)"
        )));
    }

    private static Finding finding() {
        return new Finding(
                new FindingId("slow-test:ExampleTest.slowExample"),
                "Slow observed test: slowExample",
                FindingCategory.BUILD_RUNNER,
                FindingSeverity.MEDIUM,
                Confidence.MEDIUM,
                Duration.ofMillis(1_500),
                new TimeSavingEstimate(Duration.ZERO, Duration.ofMillis(1_500)),
                List.of("ExampleTest.slowExample"),
                List.of(
                        "Observed duration 1500 ms.",
                        "Report file: TEST-ExampleTest.xml",
                        "Joined collectors: junit5-listener, maven-test-report",
                        "Module: dev.testsleuth:sample.",
                        "Build run: run-1.",
                        "Maven project: dev.testsleuth:sample:0.1.0.",
                        "Process IDs: 12345.",
                        "Fork numbers: 1.",
                        "Test runners: surefire.",
                        "Configured fork counts: 2."
                ),
                "This is one of the highest-duration tests observed in event data.",
                "Inspect this test's setup, framework initialization, fixtures, waits, and external resources.",
                "Timing-only finding. It identifies where to investigate, but does not yet prove waste.",
                "Rerun TestSleuth after changes and compare this test's observed duration."
        );
    }

    private static final class RecordingLog implements Log {
        private final List<String> infoLines = new ArrayList<>();

        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public void debug(CharSequence content) {
        }

        @Override
        public void debug(CharSequence content, Throwable error) {
        }

        @Override
        public void debug(Throwable error) {
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public void info(CharSequence content) {
            infoLines.add(content.toString());
        }

        @Override
        public void info(CharSequence content, Throwable error) {
            info(content);
        }

        @Override
        public void info(Throwable error) {
        }

        @Override
        public boolean isWarnEnabled() {
            return false;
        }

        @Override
        public void warn(CharSequence content) {
        }

        @Override
        public void warn(CharSequence content, Throwable error) {
        }

        @Override
        public void warn(Throwable error) {
        }

        @Override
        public boolean isErrorEnabled() {
            return false;
        }

        @Override
        public void error(CharSequence content) {
        }

        @Override
        public void error(CharSequence content, Throwable error) {
        }

        @Override
        public void error(Throwable error) {
        }
    }
}

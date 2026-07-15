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
        TestSleuthMavenConfig config = TestSleuthMavenConfig.from(true, "findings", 1_000, 5_000, 10, true);

        new ConsoleSummaryReporter().report(
                log,
                config,
                new MavenTestReportScanner.ScanResult(List.of()),
                3,
                List.of(finding()),
                Path.of("target/testsleuth/index.html"),
                Path.of("target/testsleuth/events.json")
        );

        assertTrue(log.infoLines.stream().anyMatch(line -> line.contains(
                "[TestSleuth] - MEDIUM Slow observed test: slowExample "
                        + "(1500 ms, module=dev.testsleuth:sample, fork=1, collectors=junit5-listener, maven-test-report)"
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
                        "Fork numbers: 1."
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

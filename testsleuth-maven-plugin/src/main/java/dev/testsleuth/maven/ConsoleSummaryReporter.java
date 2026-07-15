package dev.testsleuth.maven;

import dev.testsleuth.core.finding.Finding;
import org.apache.maven.plugin.logging.Log;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class ConsoleSummaryReporter {
    void report(
            Log log,
            TestSleuthMavenConfig config,
            MavenTestReportScanner.ScanResult scanResult,
            int junitLifecycleEventCount,
            List<Finding> findings,
            Path htmlReport,
            Path eventsFile
    ) {
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(scanResult, "scanResult");
        Objects.requireNonNull(findings, "findings");
        Objects.requireNonNull(htmlReport, "htmlReport");
        Objects.requireNonNull(eventsFile, "eventsFile");

        if (!config.consoleEnabled() || config.consoleDetail() == TestSleuthMavenConfig.ConsoleDetail.QUIET) {
            log.info("[TestSleuth] Report: " + htmlReport);
            log.info("[TestSleuth] Events: " + eventsFile);
            return;
        }

        log.info("[TestSleuth] Tests observed: " + scanResult.testCount());
        log.info("[TestSleuth] JUnit lifecycle events: " + junitLifecycleEventCount);
        log.info("[TestSleuth] Findings: " + findings.size() + " above configured thresholds");
        log.info("[TestSleuth] Slow-test threshold: " + config.slowTestThreshold().toMillis() + " ms");

        findings.stream()
                .max(Comparator.comparing(Finding::observedCost))
                .ifPresentOrElse(
                        finding -> log.info("[TestSleuth] Top finding: " + finding.title()
                                + ", " + finding.observedCost().toMillis() + " ms observed"),
                        () -> log.info("[TestSleuth] Top finding: none")
                );

        if (config.consoleDetail() == TestSleuthMavenConfig.ConsoleDetail.FINDINGS) {
            findings.stream()
                    .sorted(Comparator.comparing(Finding::observedCost).reversed())
                    .forEach(finding -> log.info("[TestSleuth] - " + finding.severity()
                            + " " + finding.title()
                            + " (" + finding.observedCost().toMillis() + " ms"
                            + contextSuffix(finding)
                            + ")"));
        }

        log.info("[TestSleuth] Report: " + htmlReport);
        log.info("[TestSleuth] Events: " + eventsFile);
    }

    private static String contextSuffix(Finding finding) {
        String module = evidenceValue(finding, "Module: ");
        String fork = evidenceValue(finding, "Fork numbers: ");
        String collectors = evidenceValue(finding, "Joined collectors: ");

        StringBuilder suffix = new StringBuilder();
        appendContext(suffix, "module", module);
        appendContext(suffix, "fork", fork);
        appendContext(suffix, "collectors", collectors);
        return suffix.toString();
    }

    private static void appendContext(StringBuilder suffix, String label, String value) {
        if (value.isBlank() || "unknown".equals(value)) {
            return;
        }
        suffix.append(", ").append(label).append("=").append(value);
    }

    private static String evidenceValue(Finding finding, String prefix) {
        return finding.evidence().stream()
                .filter(item -> item.startsWith(prefix))
                .map(item -> item.substring(prefix.length()))
                .map(ConsoleSummaryReporter::stripTrailingPeriod)
                .findFirst()
                .orElse("");
    }

    private static String stripTrailingPeriod(String value) {
        if (value.endsWith(".")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}

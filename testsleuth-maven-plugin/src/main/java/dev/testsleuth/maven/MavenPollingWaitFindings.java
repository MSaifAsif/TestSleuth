package dev.testsleuth.maven;

import dev.testsleuth.core.event.TestSleuthRunContext;
import dev.testsleuth.core.finding.Confidence;
import dev.testsleuth.core.finding.EvidenceType;
import dev.testsleuth.core.finding.Finding;
import dev.testsleuth.core.finding.AttributionScope;
import dev.testsleuth.core.finding.FindingCategory;
import dev.testsleuth.core.finding.FindingId;
import dev.testsleuth.core.finding.FindingSeverity;
import dev.testsleuth.core.finding.TimeSavingEstimate;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class MavenPollingWaitFindings {
    private final TestSleuthMavenConfig config;
    private final TestSleuthRunContext runContext;

    MavenPollingWaitFindings(TestSleuthMavenConfig config, TestSleuthRunContext runContext) {
        this.config = Objects.requireNonNull(config, "config");
        this.runContext = Objects.requireNonNull(runContext, "runContext");
    }

    List<Finding> detect(List<String> testSourceRoots) {
        Objects.requireNonNull(testSourceRoots, "testSourceRoots");
        if (!config.pollingWaitsDetectorEnabled() || config.maxFindings() == 0) {
            return List.of();
        }

        return new MavenSourceWaitScanner().scanRoots(testSourceRoots)
                .filter(MavenSourceWaitScanner.SourceWait::insideLoop)
                .filter(wait -> wait.duration().compareTo(config.pollingWaitThreshold()) >= 0)
                .sorted(Comparator.comparing(MavenSourceWaitScanner.SourceWait::duration).reversed()
                        .thenComparing(wait -> wait.source().toString())
                        .thenComparing(MavenSourceWaitScanner.SourceWait::lineNumber))
                .limit(config.maxFindings())
                .map(this::toFinding)
                .toList();
    }

    private Finding toFinding(MavenSourceWaitScanner.SourceWait wait) {
        String location = wait.source() + ":" + wait.lineNumber();
        return new Finding(
                new FindingId("polling-wait:" + sanitize(runContext.moduleId()) + ":" + sanitize(location)),
                "Polling wait in test source: " + location,
                FindingCategory.WAITING,
                severity(wait.duration()),
                Confidence.LOW,
                EvidenceType.POTENTIAL,
                AttributionScope.UNCLASSIFIED,
                Duration.ZERO,
                new TimeSavingEstimate(Duration.ZERO, Duration.ZERO),
                List.of(location),
                List.of(
                        wait.expression() + " waited up to " + wait.duration().toMillis()
                                + " ms inside a nearby loop at " + location + ".",
                        "Detector: polling-waits-source.",
                        "Module: " + runContext.moduleId() + ".",
                        "Build run: " + runContext.buildRunId() + ".",
                        "Maven project: " + runContext.projectGroupId()
                                + ":" + runContext.projectArtifactId()
                                + ":" + runContext.projectVersion() + ".",
                        "Process IDs: " + runContext.processId() + ".",
                        "Fork numbers: " + runContext.forkNumber() + "."
                ),
                "The test source appears to poll with a fixed wait interval, but this run did not prove that the loop executed or consumed the configured duration.",
                "Replace polling sleeps with condition-based waiting that exits as soon as the condition is ready, or use explicit synchronization/fakes.",
                "Potential/static finding. Confirm the loop is actually polling and is a material cost before changing behavior.",
                "Enable runtime diagnosis in a future JFR-backed run, then confirm the polling wait is observed before making a change."
        );
    }

    private FindingSeverity severity(Duration duration) {
        if (duration.toMillis() >= 1_000) {
            return FindingSeverity.HIGH;
        }
        return FindingSeverity.MEDIUM;
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._:-]", "_");
    }
}

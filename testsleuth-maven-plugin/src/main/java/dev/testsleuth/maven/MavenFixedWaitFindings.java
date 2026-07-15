package dev.testsleuth.maven;

import dev.testsleuth.core.event.TestSleuthRunContext;
import dev.testsleuth.core.finding.Confidence;
import dev.testsleuth.core.finding.Finding;
import dev.testsleuth.core.finding.FindingCategory;
import dev.testsleuth.core.finding.FindingId;
import dev.testsleuth.core.finding.FindingSeverity;
import dev.testsleuth.core.finding.TimeSavingEstimate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class MavenFixedWaitFindings {
    private static final Pattern THREAD_SLEEP = Pattern.compile("Thread\\.sleep\\(\\s*([0-9][0-9_]*)(?:[lL])?\\s*\\)");

    private final TestSleuthMavenConfig config;
    private final TestSleuthRunContext runContext;

    MavenFixedWaitFindings(TestSleuthMavenConfig config, TestSleuthRunContext runContext) {
        this.config = Objects.requireNonNull(config, "config");
        this.runContext = Objects.requireNonNull(runContext, "runContext");
    }

    List<Finding> detect(List<String> testSourceRoots) {
        Objects.requireNonNull(testSourceRoots, "testSourceRoots");
        if (!config.fixedWaitsDetectorEnabled() || config.maxFindings() == 0) {
            return List.of();
        }

        return testSourceRoots.stream()
                .filter(root -> root != null && !root.isBlank())
                .map(Path::of)
                .filter(Files::isDirectory)
                .flatMap(this::scanRoot)
                .filter(wait -> wait.duration().compareTo(config.fixedWaitThreshold()) >= 0)
                .sorted(Comparator.comparing(FixedWait::duration).reversed()
                        .thenComparing(wait -> wait.source().toString())
                        .thenComparing(FixedWait::lineNumber))
                .limit(config.maxFindings())
                .map(this::toFinding)
                .toList();
    }

    private Stream<FixedWait> scanRoot(Path root) {
        try {
            return Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .flatMap(path -> scanFile(root, path).stream());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan test source root " + root, e);
        }
    }

    private List<FixedWait> scanFile(Path root, Path source) {
        try {
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            java.util.ArrayList<FixedWait> waits = new java.util.ArrayList<>();
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (line.stripLeading().startsWith("//")) {
                    continue;
                }
                Matcher matcher = THREAD_SLEEP.matcher(line);
                while (matcher.find()) {
                    waits.add(new FixedWait(
                            root.relativize(source),
                            index + 1,
                            Duration.ofMillis(parseMillis(matcher.group(1)))
                    ));
                }
            }
            return waits;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan test source file " + source, e);
        }
    }

    private Finding toFinding(FixedWait wait) {
        String location = wait.source() + ":" + wait.lineNumber();
        return new Finding(
                new FindingId("fixed-wait:" + sanitize(runContext.moduleId()) + ":" + sanitize(location)),
                "Fixed wait in test source: " + location,
                FindingCategory.WAITING,
                severity(wait.duration()),
                Confidence.HIGH,
                wait.duration(),
                new TimeSavingEstimate(Duration.ZERO, wait.duration()),
                List.of(location),
                List.of(
                        "Thread.sleep(" + wait.duration().toMillis() + " ms) at " + location + ".",
                        "Detector: fixed-waits-source.",
                        "Module: " + runContext.moduleId() + ".",
                        "Build run: " + runContext.buildRunId() + ".",
                        "Maven project: " + runContext.projectGroupId()
                                + ":" + runContext.projectArtifactId()
                                + ":" + runContext.projectVersion() + ".",
                        "Process IDs: " + runContext.processId() + ".",
                        "Fork numbers: " + runContext.forkNumber() + "."
                ),
                "The test source contains a fixed sleep, which usually waits the full duration even when the condition is ready earlier.",
                "Replace fixed sleeps with condition-based waiting, callbacks, fakes, or explicit synchronization.",
                "Source-only finding. Confirm the wait is not intentionally modeling elapsed time before changing it.",
                "Rerun TestSleuth and confirm the finding disappears or the observed test duration falls."
        );
    }

    private FindingSeverity severity(Duration duration) {
        if (duration.toMillis() >= 1_000) {
            return FindingSeverity.HIGH;
        }
        return FindingSeverity.MEDIUM;
    }

    private static long parseMillis(String literal) {
        return Long.parseLong(literal.replace("_", ""));
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._:-]", "_");
    }

    private record FixedWait(Path source, int lineNumber, Duration duration) {
    }
}

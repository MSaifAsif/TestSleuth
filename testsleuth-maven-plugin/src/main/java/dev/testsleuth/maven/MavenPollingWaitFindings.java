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

final class MavenPollingWaitFindings {
    private static final Pattern THREAD_SLEEP = Pattern.compile("Thread\\.sleep\\(\\s*([0-9][0-9_]*)(?:[lL])?\\s*\\)");
    private static final Pattern LOOP = Pattern.compile("\\b(for|while)\\s*\\(");

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

        return testSourceRoots.stream()
                .filter(root -> root != null && !root.isBlank())
                .map(Path::of)
                .filter(Files::isDirectory)
                .flatMap(this::scanRoot)
                .filter(wait -> wait.duration().compareTo(config.pollingWaitThreshold()) >= 0)
                .sorted(Comparator.comparing(PollingWait::duration).reversed()
                        .thenComparing(wait -> wait.source().toString())
                        .thenComparing(PollingWait::lineNumber))
                .limit(config.maxFindings())
                .map(this::toFinding)
                .toList();
    }

    private Stream<PollingWait> scanRoot(Path root) {
        try {
            return Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .flatMap(path -> scanFile(root, path).stream());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan test source root " + root, e);
        }
    }

    private List<PollingWait> scanFile(Path root, Path source) {
        try {
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            java.util.ArrayList<PollingWait> waits = new java.util.ArrayList<>();
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (line.stripLeading().startsWith("//")) {
                    continue;
                }
                Matcher matcher = THREAD_SLEEP.matcher(line);
                while (matcher.find()) {
                    if (nearLoop(lines, index)) {
                        waits.add(new PollingWait(
                                root.relativize(source),
                                index + 1,
                                Duration.ofMillis(parseMillis(matcher.group(1)))
                        ));
                    }
                }
            }
            return waits;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan test source file " + source, e);
        }
    }

    private Finding toFinding(PollingWait wait) {
        String location = wait.source() + ":" + wait.lineNumber();
        return new Finding(
                new FindingId("polling-wait:" + sanitize(runContext.moduleId()) + ":" + sanitize(location)),
                "Polling wait in test source: " + location,
                FindingCategory.WAITING,
                severity(wait.duration()),
                Confidence.MEDIUM,
                wait.duration(),
                new TimeSavingEstimate(Duration.ZERO, wait.duration()),
                List.of(location),
                List.of(
                        "Thread.sleep(" + wait.duration().toMillis() + " ms) inside a nearby loop at " + location + ".",
                        "Detector: polling-waits-source.",
                        "Module: " + runContext.moduleId() + ".",
                        "Build run: " + runContext.buildRunId() + ".",
                        "Maven project: " + runContext.projectGroupId()
                                + ":" + runContext.projectArtifactId()
                                + ":" + runContext.projectVersion() + ".",
                        "Process IDs: " + runContext.processId() + ".",
                        "Fork numbers: " + runContext.forkNumber() + "."
                ),
                "The test source appears to poll with a fixed sleep interval, which can add repeated waiting or hide synchronization gaps.",
                "Replace polling sleeps with condition-based waiting that exits as soon as the condition is ready, or use explicit synchronization/fakes.",
                "Source-only finding. Confirm the loop is actually polling before changing behavior.",
                "Rerun TestSleuth and confirm the finding disappears or the observed test duration falls."
        );
    }

    private FindingSeverity severity(Duration duration) {
        if (duration.toMillis() >= 1_000) {
            return FindingSeverity.HIGH;
        }
        return FindingSeverity.MEDIUM;
    }

    private static boolean nearLoop(List<String> lines, int sleepLineIndex) {
        int start = Math.max(0, sleepLineIndex - 4);
        for (int index = sleepLineIndex; index >= start; index--) {
            String line = lines.get(index).stripLeading();
            if (line.startsWith("//")) {
                continue;
            }
            if (LOOP.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    private static long parseMillis(String literal) {
        return Long.parseLong(literal.replace("_", ""));
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._:-]", "_");
    }

    private record PollingWait(Path source, int lineNumber, Duration duration) {
    }
}

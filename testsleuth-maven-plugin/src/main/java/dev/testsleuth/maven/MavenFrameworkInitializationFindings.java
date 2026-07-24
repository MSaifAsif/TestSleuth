package dev.testsleuth.maven;

import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.TestSleuthEvent;
import dev.testsleuth.core.event.TestSleuthRunContext;
import dev.testsleuth.core.finding.Confidence;
import dev.testsleuth.core.finding.EvidenceType;
import dev.testsleuth.core.finding.Finding;
import dev.testsleuth.core.finding.AttributionScope;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class MavenFrameworkInitializationFindings {
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([A-Za-z0-9_.]+)\\s*;");
    private static final Pattern CLASS = Pattern.compile("\\b(?:class|record)\\s+([A-Za-z0-9_]+)\\b");
    private static final Map<String, String> INDICATORS = Map.of(
            "SpringBootTest", "@SpringBootTest",
            "ApplicationContext", "application context usage",
            "SpringApplication.run", "SpringApplication.run(...)",
            "SpringExtension", "SpringExtension",
            "DirtiesContext", "@DirtiesContext",
            "MockBean", "@MockBean",
            "DynamicPropertySource", "@DynamicPropertySource",
            "SimulatedApplicationContext", "simulated application context"
    );

    private final TestSleuthMavenConfig config;
    private final TestSleuthRunContext runContext;

    MavenFrameworkInitializationFindings(TestSleuthMavenConfig config, TestSleuthRunContext runContext) {
        this.config = Objects.requireNonNull(config, "config");
        this.runContext = Objects.requireNonNull(runContext, "runContext");
    }

    List<Finding> detect(List<String> testSourceRoots, List<TestSleuthEvent> events) {
        return detect(testSourceRoots, events, java.util.Set.of());
    }

    List<Finding> detect(
            List<String> testSourceRoots,
            List<TestSleuthEvent> events,
            java.util.Set<String> runtimeBackedClassNames
    ) {
        Objects.requireNonNull(testSourceRoots, "testSourceRoots");
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(runtimeBackedClassNames, "runtimeBackedClassNames");
        if (!config.frameworkInitializationDetectorEnabled() || config.maxFindings() == 0) {
            return List.of();
        }

        Map<String, Duration> classDurations = classDurations(events);
        if (classDurations.isEmpty()) {
            return List.of();
        }

        return testSourceRoots.stream()
                .filter(root -> root != null && !root.isBlank())
                .map(Path::of)
                .filter(Files::isDirectory)
                .flatMap(root -> scanRoot(root, classDurations))
                .filter(candidate -> !runtimeBackedClassNames.contains(candidate.className()))
                .filter(candidate -> candidate.duration().compareTo(config.slowTestThreshold()) >= 0)
                .sorted(Comparator.comparing(FrameworkCandidate::duration).reversed()
                        .thenComparing(candidate -> candidate.source().toString()))
                .limit(config.maxFindings())
                .map(this::toFinding)
                .toList();
    }

    private Stream<FrameworkCandidate> scanRoot(Path root, Map<String, Duration> classDurations) {
        try {
            return Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .flatMap(path -> scanFile(root, path, classDurations).stream());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan test source root " + root, e);
        }
    }

    private Optional<FrameworkCandidate> scanFile(Path root, Path source, Map<String, Duration> classDurations) {
        try {
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            Optional<String> className = className(lines);
            if (className.isEmpty()) {
                return Optional.empty();
            }
            Duration duration = classDurations.getOrDefault(className.orElseThrow(), Duration.ZERO);
            LinkedHashSet<String> indicators = indicators(lines);
            if (indicators.isEmpty() || duration.isZero()) {
                return Optional.empty();
            }
            return Optional.of(new FrameworkCandidate(
                    root.relativize(source),
                    className.orElseThrow(),
                    duration,
                    List.copyOf(indicators)
            ));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan test source file " + source, e);
        }
    }

    private Finding toFinding(FrameworkCandidate candidate) {
        String location = candidate.source().toString();
        return new Finding(
                new FindingId("framework-initialization:" + sanitize(runContext.moduleId()) + ":" + sanitize(candidate.className())),
                "Framework initialization candidate: " + candidate.className(),
                FindingCategory.SPRING_CONTEXT,
                severity(candidate.duration()),
                Confidence.LOW,
                EvidenceType.POTENTIAL,
                AttributionScope.UNCLASSIFIED,
                Duration.ZERO,
                new TimeSavingEstimate(Duration.ZERO, Duration.ZERO),
                List.of(location, candidate.className()),
                List.of(
                        "Observed class duration " + candidate.duration().toMillis() + " ms for " + candidate.className() + ".",
                        "Framework indicators: " + String.join(", ", candidate.indicators()) + ".",
                        "Source: " + location + ".",
                        "Detector: framework-initialization-source.",
                        "Module: " + runContext.moduleId() + ".",
                        "Build run: " + runContext.buildRunId() + ".",
                        "Maven project: " + runContext.projectGroupId()
                                + ":" + runContext.projectArtifactId()
                                + ":" + runContext.projectVersion() + ".",
                        "Process IDs: " + runContext.processId() + ".",
                        "Fork numbers: " + runContext.forkNumber() + "."
                ),
                "The test class has framework/application-context indicators and is slow, but this run did not measure framework startup as the cause.",
                "Check whether the test needs a full application context, whether similar contexts can be reused, or whether a narrower test slice/fake is sufficient.",
                "Potential/static finding. Confirm framework startup is the dominant cost before changing test scope.",
                "Use a future JFR-backed run to measure framework spans before changing test scope."
        );
    }

    private static Map<String, Duration> classDurations(List<TestSleuthEvent> events) {
        Map<String, Long> mavenDurations = durationByClass(events, "maven-test-report");
        if (!mavenDurations.isEmpty()) {
            return toDurations(mavenDurations);
        }
        return toDurations(durationByClass(events, "junit5-listener"));
    }

    private static Map<String, Long> durationByClass(List<TestSleuthEvent> events, String collector) {
        java.util.HashMap<String, Long> durations = new java.util.HashMap<>();
        for (TestSleuthEvent event : events) {
            if (event.kind() != EventKind.TEST_FINISHED || !collector.equals(event.attributes().get("collector"))) {
                continue;
            }
            String className = event.attributes().get("className");
            if (className == null || className.isBlank()) {
                continue;
            }
            durations.merge(className, durationMillis(event), Long::sum);
        }
        return durations;
    }

    private static Map<String, Duration> toDurations(Map<String, Long> millisByClass) {
        return millisByClass.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Duration.ofMillis(entry.getValue())
                ));
    }

    private static Optional<String> className(List<String> lines) {
        String packageName = "";
        String simpleClassName = "";
        for (String line : lines) {
            Matcher packageMatcher = PACKAGE.matcher(line);
            if (packageMatcher.find()) {
                packageName = packageMatcher.group(1);
            }
            Matcher classMatcher = CLASS.matcher(line);
            if (classMatcher.find()) {
                simpleClassName = classMatcher.group(1);
                break;
            }
        }
        if (simpleClassName.isBlank()) {
            return Optional.empty();
        }
        if (packageName.isBlank()) {
            return Optional.of(simpleClassName);
        }
        return Optional.of(packageName + "." + simpleClassName);
    }

    private static LinkedHashSet<String> indicators(List<String> lines) {
        LinkedHashSet<String> indicators = new LinkedHashSet<>();
        for (String line : lines) {
            if (line.stripLeading().startsWith("//")) {
                continue;
            }
            for (Map.Entry<String, String> indicator : INDICATORS.entrySet()) {
                if (line.contains(indicator.getKey())) {
                    indicators.add(indicator.getValue());
                }
            }
        }
        return indicators;
    }

    private FindingSeverity severity(Duration duration) {
        if (duration.compareTo(config.verySlowTestThreshold()) >= 0) {
            return FindingSeverity.HIGH;
        }
        return FindingSeverity.MEDIUM;
    }

    private static long durationMillis(TestSleuthEvent event) {
        try {
            return Math.max(0, Long.parseLong(event.attributes().getOrDefault("durationMillis", "0")));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._:-]", "_");
    }

    private record FrameworkCandidate(Path source, String className, Duration duration, List<String> indicators) {
    }

    static List<FrameworkSource> frameworkSources(List<String> testSourceRoots) {
        Objects.requireNonNull(testSourceRoots, "testSourceRoots");
        return testSourceRoots.stream()
                .filter(root -> root != null && !root.isBlank())
                .map(Path::of)
                .filter(Files::isDirectory)
                .flatMap(MavenFrameworkInitializationFindings::scanFrameworkSources)
                .toList();
    }

    private static Stream<FrameworkSource> scanFrameworkSources(Path root) {
        try {
            return Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .flatMap(path -> frameworkSource(root, path).stream());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan test source root " + root, e);
        }
    }

    private static Optional<FrameworkSource> frameworkSource(Path root, Path source) {
        try {
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            Optional<String> className = className(lines);
            LinkedHashSet<String> indicators = indicators(lines);
            if (className.isEmpty() || indicators.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new FrameworkSource(root.relativize(source), className.orElseThrow(), List.copyOf(indicators)));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan test source file " + source, e);
        }
    }

    record FrameworkSource(Path source, String className, List<String> indicators) {
    }
}

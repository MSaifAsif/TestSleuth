package dev.testsleuth.maven;

import dev.testsleuth.core.finding.AttributionScope;
import dev.testsleuth.core.finding.Confidence;
import dev.testsleuth.core.finding.EvidenceType;
import dev.testsleuth.core.finding.Finding;
import dev.testsleuth.core.finding.FindingCategory;
import dev.testsleuth.core.finding.FindingId;
import dev.testsleuth.core.finding.FindingSeverity;
import dev.testsleuth.core.finding.TimeSavingEstimate;

import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Correlates direct JFR timing with framework-indicator source only when the observed stack belongs to that source
 * class.
 */
final class MavenJfrFrameworkInitializationFindings {
    private final TestSleuthMavenConfig config;
    private final dev.testsleuth.core.event.TestSleuthRunContext runContext;

    MavenJfrFrameworkInitializationFindings(
            TestSleuthMavenConfig config,
            dev.testsleuth.core.event.TestSleuthRunContext runContext
    ) {
        this.config = config;
        this.runContext = runContext;
    }

    Result detect(List<String> testSourceRoots, MavenJfrTestAttribution.Summary attribution) {
        if (!config.frameworkInitializationDetectorEnabled() || config.maxFindings() == 0) {
            return Result.empty();
        }
        Map<String, RuntimeEvidence> evidenceByClass = runtimeEvidenceByClass(attribution);
        List<Candidate> candidates = MavenFrameworkInitializationFindings.frameworkSources(testSourceRoots).stream()
                .map(source -> candidate(source, evidenceByClass.get(source.className())))
                .flatMap(java.util.Optional::stream)
                .filter(candidate -> candidate.evidence.duration().compareTo(config.slowTestThreshold()) >= 0)
                .sorted(Comparator.comparing((Candidate candidate) -> candidate.evidence.duration()).reversed()
                        .thenComparing(candidate -> candidate.source.className()))
                .limit(config.maxFindings())
                .toList();
        return new Result(candidates.stream().map(this::toFinding).toList(), candidates.stream()
                .map(candidate -> candidate.source.className())
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    private static Map<String, RuntimeEvidence> runtimeEvidenceByClass(MavenJfrTestAttribution.Summary attribution) {
        Map<String, RuntimeEvidence> evidenceByClass = new LinkedHashMap<>();
        attribution.evidence().forEach(evidence -> addEvidence(
                evidence.testIdentity(),
                "test body",
                evidence.eventDurations(),
                evidence.eventLocations(),
                evidenceByClass
        ));
        attribution.phaseEvidence().forEach(evidence -> addEvidence(
                evidence.testIdentity(),
                evidence.phase(),
                evidence.eventDurations(),
                evidence.eventLocations(),
                evidenceByClass
        ));
        return Map.copyOf(evidenceByClass);
    }

    private static void addEvidence(
            String testIdentity,
            String phase,
            Map<String, Duration> durations,
            Map<String, String> locations,
            Map<String, RuntimeEvidence> evidenceByClass
    ) {
        String className = className(testIdentity);
        if (className.isBlank()) {
            return;
        }
        durations.forEach((family, duration) -> {
            String location = locations.getOrDefault(family, "");
            if (duration.isZero() || !belongsToSourceClass(location, className)) {
                return;
            }
            evidenceByClass.computeIfAbsent(className, ignored -> new RuntimeEvidence())
                    .add(family, duration, location, phase);
        });
    }

    private static java.util.Optional<Candidate> candidate(
            MavenFrameworkInitializationFindings.FrameworkSource source,
            RuntimeEvidence evidence
    ) {
        if (evidence == null || evidence.duration().isZero()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Candidate(source, evidence));
    }

    private Finding toFinding(Candidate candidate) {
        RuntimeEvidence evidence = candidate.evidence;
        return new Finding(
                new FindingId("jfr-framework-initialization:" + sanitize(runContext.moduleId()) + ":"
                        + sanitize(candidate.source.className())),
                "Runtime-backed framework initialization: " + candidate.source.className(),
                FindingCategory.SPRING_CONTEXT,
                severity(evidence.duration()),
                Confidence.MEDIUM,
                EvidenceType.INFERRED,
                AttributionScope.FRAMEWORK_OR_FIXTURE,
                evidence.duration(),
                new TimeSavingEstimate(Duration.ZERO, evidence.duration()),
                List.of(candidate.source.className(), candidate.source.source().toString()),
                List.of(
                        "JFR directly attributed " + evidence.duration().toMillis()
                                + " ms to framework-indicator source code.",
                        "Observed runtime causes: " + evidence.families() + ".",
                        "Observed phases: " + evidence.phases() + ".",
                        "User-code stack: " + evidence.location() + ".",
                        "Framework indicators: " + String.join(", ", candidate.source.indicators()) + ".",
                        "Source: " + candidate.source.source() + "."
                ),
                "Runtime cost occurred in source code that initializes or accesses a framework/application context; "
                        + "the semantic framework cause is inferred from direct JFR timing plus source indicators.",
                "Reuse an equivalent context where safe, narrow the test slice, or replace the full context with a "
                        + "focused fake.",
                "The elapsed time is directly measured, but JFR does not name the framework operation itself; "
                        + "verify the proposed context change against isolation requirements.",
                "Rerun with JFR enabled and confirm both the directly attributed duration and repeated context "
                        + "initialization count fall."
        );
    }

    private FindingSeverity severity(Duration duration) {
        if (duration.compareTo(config.verySlowTestThreshold()) >= 0) {
            return FindingSeverity.HIGH;
        }
        return FindingSeverity.MEDIUM;
    }

    private static String className(String testIdentity) {
        int separator = testIdentity.lastIndexOf('.');
        return separator > 0 ? testIdentity.substring(0, separator) : "";
    }

    private static boolean belongsToSourceClass(String location, String className) {
        return location.startsWith(className + ".") || location.startsWith(className + "$");
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._:-]", "_");
    }

    private record Candidate(MavenFrameworkInitializationFindings.FrameworkSource source, RuntimeEvidence evidence) {
    }

    private static final class RuntimeEvidence {
        private final Map<String, Duration> durations = new LinkedHashMap<>();
        private final java.util.LinkedHashSet<String> phases = new java.util.LinkedHashSet<>();
        private String location = "";

        private void add(String family, Duration duration, String location, String phase) {
            durations.merge(family, duration, Duration::plus);
            phases.add(phase);
            if (this.location.isBlank()) {
                this.location = location;
            }
        }

        private Duration duration() {
            return durations.values().stream().reduce(Duration.ZERO, Duration::plus);
        }

        private Map<String, Long> families() {
            return durations.entrySet().stream().collect(java.util.stream.Collectors.collectingAndThen(
                    java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().toMillis(),
                            Long::sum,
                            LinkedHashMap::new
                    ),
                    Map::copyOf
            ));
        }

        private List<String> phases() {
            return List.copyOf(phases);
        }

        private String location() {
            return location;
        }
    }

    record Result(List<Finding> findings, java.util.Set<String> runtimeBackedClassNames) {
        Result {
            findings = List.copyOf(findings);
            runtimeBackedClassNames = java.util.Set.copyOf(runtimeBackedClassNames);
        }

        static Result empty() {
            return new Result(List.of(), java.util.Set.of());
        }
    }
}

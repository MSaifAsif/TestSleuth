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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Identifies the same directly measured runtime operation recurring across multiple tests. */
final class MavenJfrRepetitionFindings {
    private static final int MINIMUM_DISTINCT_TESTS = 2;

    private final TestSleuthMavenConfig config;

    MavenJfrRepetitionFindings(TestSleuthMavenConfig config) {
        this.config = config;
    }

    List<Finding> detect(MavenJfrTestAttribution.Summary attribution) {
        if (config.maxFindings() == 0) {
            return List.of();
        }
        Map<Operation, RepeatedOperation> repetitions = new LinkedHashMap<>();
        attribution.evidence().forEach(evidence -> addEvidence(
                evidence.testIdentity(),
                "test body",
                evidence.eventDurations(),
                evidence.eventLocations(),
                repetitions
        ));
        attribution.phaseEvidence().forEach(evidence -> addEvidence(
                evidence.testIdentity(),
                evidence.phase(),
                evidence.eventDurations(),
                evidence.eventLocations(),
                repetitions
        ));
        return repetitions.values().stream()
                .filter(repetition -> repetition.tests().size() >= MINIMUM_DISTINCT_TESTS)
                .filter(repetition -> repetition.totalDuration().compareTo(config.slowTestThreshold()) >= 0)
                .sorted(Comparator.comparing(RepeatedOperation::totalDuration).reversed()
                        .thenComparing(repetition -> repetition.operation().stackLocation()))
                .limit(config.maxFindings())
                .map(this::toFinding)
                .toList();
    }

    private static void addEvidence(
            String testIdentity,
            String phase,
            Map<String, Duration> durations,
            Map<String, String> locations,
            Map<Operation, RepeatedOperation> repetitions
    ) {
        durations.forEach((family, duration) -> {
            String stackLocation = locations.getOrDefault(family, "");
            if (duration.isZero() || stackLocation.isBlank()) {
                return;
            }
            Operation operation = new Operation(family, stackLocation);
            repetitions.computeIfAbsent(operation, RepeatedOperation::new).add(testIdentity, phase, duration);
        });
    }

    private Finding toFinding(RepeatedOperation repetition) {
        Operation operation = repetition.operation();
        Duration totalDuration = repetition.totalDuration();
        String cause = causeLabel(operation.family());
        return new Finding(
                new FindingId("jfr-repetition:" + sanitize(operation.family()) + ":"
                        + sanitize(operation.stackLocation())),
                "Repeated JFR " + cause + ": " + operation.stackLocation(),
                category(operation.family()),
                severity(totalDuration),
                Confidence.HIGH,
                EvidenceType.MEASURED,
                AttributionScope.DIRECT_TEST_THREAD,
                totalDuration,
                new TimeSavingEstimate(Duration.ZERO, totalDuration),
                List.copyOf(repetition.tests()),
                List.of(
                        "JFR directly attributed " + operation.family() + " at the same user-code stack in "
                                + repetition.tests().size() + " tests.",
                        "Total observed duration: " + totalDuration.toMillis() + " ms.",
                        "Longest single observation: " + repetition.longestDuration().toMillis() + " ms.",
                        "Observed phases: " + List.copyOf(repetition.phases()) + ".",
                        "User-code stack: " + operation.stackLocation() + "."
                ),
                "The same directly measured " + cause + " operation recurred across multiple tests.",
                action(operation.family()),
                "This total is the sum of directly owned test costs. It is not additional time beyond the individual "
                        + "JFR findings.",
                "Rerun with JFR enabled and confirm the repeated operation count and total observed duration fall."
        );
    }

    private FindingSeverity severity(Duration duration) {
        if (duration.compareTo(config.verySlowTestThreshold()) >= 0) {
            return FindingSeverity.HIGH;
        }
        return FindingSeverity.MEDIUM;
    }

    private static String causeLabel(String family) {
        return switch (family) {
            case "ThreadSleep" -> "fixed wait";
            case "ThreadPark" -> "parked or polling wait";
            case "SocketRead", "SocketWrite" -> "external I/O";
            case "MonitorEnter" -> "lock contention";
            case "FileRead", "FileWrite" -> "file I/O";
            case "ClassLoad" -> "class loading and warm-up";
            default -> family;
        };
    }

    private static FindingCategory category(String family) {
        return switch (family) {
            case "ThreadSleep", "ThreadPark" -> FindingCategory.WAITING;
            case "MonitorEnter" -> FindingCategory.PARALLELISM;
            case "FileRead", "FileWrite" -> FindingCategory.FIXTURE;
            default -> FindingCategory.GENERAL;
        };
    }

    private static String action(String family) {
        return switch (family) {
            case "ThreadSleep", "ThreadPark" -> "Replace the common wait with a condition-driven completion signal "
                    + "or mock the boundary.";
            case "SocketRead", "SocketWrite" -> "Reuse or virtualize the shared integration boundary across the "
                    + "affected tests.";
            case "MonitorEnter" -> "Remove shared mutable fixtures or reduce synchronization at the repeated "
                    + "contention point.";
            case "FileRead", "FileWrite" -> "Reuse the repeated fixture or move the common file operation behind "
                    + "an in-memory seam.";
            case "ClassLoad" -> "Move one-time initialization out of repeated test paths and compare first-test "
                    + "versus steady-state cost.";
            default -> "Inspect the shared stack and remove the repeated operation where test isolation allows it.";
        };
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._:-]", "_");
    }

    private record Operation(String family, String stackLocation) {
    }

    private static final class RepeatedOperation {
        private final Operation operation;
        private final LinkedHashSet<String> tests = new LinkedHashSet<>();
        private final LinkedHashSet<String> phases = new LinkedHashSet<>();
        private final List<Duration> durations = new ArrayList<>();

        private RepeatedOperation(Operation operation) {
            this.operation = operation;
        }

        private void add(String testIdentity, String phase, Duration duration) {
            tests.add(testIdentity);
            phases.add(phase);
            durations.add(duration);
        }

        private Operation operation() {
            return operation;
        }

        private LinkedHashSet<String> tests() {
            return tests;
        }

        private LinkedHashSet<String> phases() {
            return phases;
        }

        private Duration totalDuration() {
            return durations.stream().reduce(Duration.ZERO, Duration::plus);
        }

        private Duration longestDuration() {
            return durations.stream().max(Comparator.naturalOrder()).orElse(Duration.ZERO);
        }
    }
}

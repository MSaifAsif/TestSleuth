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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Projects attributed JFR runtime evidence into report findings without overstating sampled or shared costs. */
final class MavenJfrRuntimeFindings {
    private final TestSleuthMavenConfig config;

    MavenJfrRuntimeFindings(TestSleuthMavenConfig config) {
        this.config = config;
    }

    List<Finding> detect(MavenJfrTestAttribution.Summary attribution) {
        List<Finding> findings = new ArrayList<>();
        attribution.evidence().stream()
                .map(evidence -> measuredFinding(evidence.testIdentity(), "test body", evidence.eventDurations(), evidence.eventLocations(), AttributionScope.DIRECT_TEST_THREAD))
                .flatMap(Optional::stream)
                .forEach(findings::add);
        attribution.phaseEvidence().stream()
                .map(evidence -> measuredFinding(evidence.testIdentity(), evidence.phase(), evidence.eventDurations(), evidence.eventLocations(), AttributionScope.FRAMEWORK_OR_FIXTURE))
                .flatMap(Optional::stream)
                .forEach(findings::add);
        attribution.samples().stream()
                .map(evidence -> sampledFinding(evidence.testIdentity(), "test body", evidence.sampleCounts(), evidence.sampleLocations(), AttributionScope.DIRECT_TEST_THREAD))
                .flatMap(Optional::stream)
                .forEach(findings::add);
        attribution.phaseSamples().stream()
                .map(evidence -> sampledFinding(evidence.testIdentity(), evidence.phase(), evidence.sampleCounts(), evidence.sampleLocations(), AttributionScope.FRAMEWORK_OR_FIXTURE))
                .flatMap(Optional::stream)
                .forEach(findings::add);
        attribution.unassignedEvidence().stream()
                .map(this::garbageCollectionFinding)
                .flatMap(Optional::stream)
                .forEach(findings::add);
        return findings.stream()
                .sorted(Comparator.comparing(Finding::observedCost).reversed().thenComparing(Finding::title))
                .toList();
    }

    private Optional<Finding> measuredFinding(
            String testIdentity,
            String location,
            Map<String, Duration> durations,
            Map<String, String> locations,
            AttributionScope scope
    ) {
        return durations.entrySet().stream().max(Comparator.comparing(Map.Entry::getValue)).map(entry -> {
            Cause cause = Cause.forEvent(entry.getKey());
            Duration duration = entry.getValue();
            List<String> evidence = new ArrayList<>(List.of(
                    "JFR directly attributed " + entry.getKey() + " to " + location + ".",
                    "Observed duration: " + duration.toMillis() + " ms."
            ));
            String stack = locations.getOrDefault(entry.getKey(), "");
            if (!stack.isBlank()) {
                evidence.add("User-code stack: " + stack + ".");
            }
            return new Finding(
                    new FindingId("jfr:" + sanitize(testIdentity) + ":" + sanitize(location) + ":" + entry.getKey()),
                    "JFR " + cause.label + ": " + testIdentity,
                    cause.category,
                    severity(duration),
                    Confidence.HIGH,
                    EvidenceType.MEASURED,
                    scope,
                    duration,
                    new TimeSavingEstimate(Duration.ZERO, duration),
                    List.of(testIdentity),
                    evidence,
                    "JFR directly observed " + cause.label + " in the " + location + ".",
                    cause.action,
                    "This cost is directly attributed to the test thread or lifecycle phase; avoid double-counting it with overlapping slow-test findings.",
                    "Rerun with JFR enabled and confirm the attributed " + entry.getKey() + " duration falls."
            );
        });
    }

    private Optional<Finding> sampledFinding(
            String testIdentity,
            String location,
            Map<String, Long> counts,
            Map<String, String> locations,
            AttributionScope scope
    ) {
        return counts.entrySet().stream().max(Comparator.comparing(Map.Entry::getValue)).map(entry -> {
            Cause cause = Cause.forEvent(entry.getKey());
            List<String> evidence = new ArrayList<>(List.of(
                    "JFR directly attributed " + entry.getValue() + " " + entry.getKey() + " samples to " + location + ".",
                    "Sample counts indicate activity, not elapsed time."
            ));
            String stack = locations.getOrDefault(entry.getKey(), "");
            if (!stack.isBlank()) {
                evidence.add("User-code stack: " + stack + ".");
            }
            return new Finding(
                    new FindingId("jfr-sample:" + sanitize(testIdentity) + ":" + sanitize(location) + ":" + entry.getKey()),
                    "JFR sampled " + cause.label + ": " + testIdentity,
                    cause.category,
                    FindingSeverity.LOW,
                    Confidence.MEDIUM,
                    EvidenceType.CORRELATED,
                    scope,
                    Duration.ZERO,
                    new TimeSavingEstimate(Duration.ZERO, Duration.ZERO),
                    List.of(testIdentity),
                    evidence,
                    "JFR sampled " + cause.label + " on the directly attributed execution path.",
                    cause.action,
                    "Sampling identifies likely hotspots but does not measure elapsed or recoverable time.",
                    "Rerun with JFR enabled and compare sample counts and sampled stacks after the change."
            );
        });
    }

    private Optional<Finding> garbageCollectionFinding(MavenJfrTestAttribution.UnassignedEvidence evidence) {
        Duration pauseDuration = evidence.eventDurations().get("GarbageCollection");
        if (pauseDuration == null) {
            return Optional.empty();
        }
        AttributionScope scope = evidence.scope() == MavenJfrTestAttribution.UnassignedScope.SHARED_JVM
                ? AttributionScope.SHARED_JVM : AttributionScope.UNCLASSIFIED;
        return Optional.of(new Finding(
                new FindingId("jfr-gc:" + evidence.scope().name()),
                "JFR garbage-collection pause",
                FindingCategory.GENERAL,
                severity(pauseDuration),
                Confidence.MEDIUM,
                EvidenceType.CORRELATED,
                scope,
                pauseDuration,
                new TimeSavingEstimate(Duration.ZERO, Duration.ZERO),
                List.of("JVM"),
                List.of(
                        "JFR observed " + evidence.eventCounts().get("GarbageCollection") + " garbage-collection events.",
                        "Total pause time: " + pauseDuration.toMillis() + " ms."
                ),
                scope == AttributionScope.SHARED_JVM
                        ? "Garbage-collection pauses overlapped active test execution."
                        : "Garbage-collection pauses were observed outside directly attributable test windows.",
                "Inspect allocation-heavy sampled stacks and heap configuration before changing individual tests.",
                "GC pause time is JVM-wide evidence and is never assigned as a single test's cost.",
                "Rerun with JFR enabled and compare garbage-collection pause time after reducing allocation pressure."
        ));
    }

    private FindingSeverity severity(Duration duration) {
        if (duration.compareTo(config.verySlowTestThreshold()) >= 0) {
            return FindingSeverity.HIGH;
        }
        if (duration.compareTo(config.slowTestThreshold()) >= 0) {
            return FindingSeverity.MEDIUM;
        }
        return FindingSeverity.LOW;
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._:-]", "_");
    }

    private record Cause(String label, FindingCategory category, String action) {
        private static Cause forEvent(String eventFamily) {
            return switch (eventFamily) {
                case "ThreadSleep" -> new Cause("fixed wait", FindingCategory.WAITING,
                        "Replace the fixed delay with a condition-driven wait or mock the boundary.");
                case "ThreadPark" -> new Cause("parked or polling wait", FindingCategory.WAITING,
                        "Inspect executor, retry, and polling behavior; await a deterministic completion signal.");
                case "SocketRead", "SocketWrite" -> new Cause("external I/O", FindingCategory.GENERAL,
                        "Stub or virtualize the network boundary, or narrow the integration scope.");
                case "MonitorEnter" -> new Cause("lock contention", FindingCategory.PARALLELISM,
                        "Remove shared mutable fixtures or reduce synchronization on the test path.");
                case "FileRead", "FileWrite" -> new Cause("file I/O", FindingCategory.FIXTURE,
                        "Reuse fixtures or replace disk access with a focused in-memory test seam.");
                case "ClassLoad" -> new Cause("class loading and warm-up", FindingCategory.GENERAL,
                        "Move one-time initialization out of repeated test paths and compare first-test versus steady-state cost.");
                case "ExecutionSample" -> new Cause("CPU activity", FindingCategory.GENERAL,
                        "Inspect the sampled stack and reduce repeated computation, fixture construction, serialization, or reflection.");
                case "ObjectAllocationSample" -> new Cause("allocation activity", FindingCategory.FIXTURE,
                        "Inspect the sampled stack and reuse large fixtures or avoid repeated object construction.");
                default -> new Cause(eventFamily, FindingCategory.GENERAL, "Inspect the attributed JFR evidence.");
            };
        }
    }
}

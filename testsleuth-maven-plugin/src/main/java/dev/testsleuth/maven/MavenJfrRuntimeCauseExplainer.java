package dev.testsleuth.maven;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Converts directly owned JFR event duration into concise, evidence-backed remediation guidance. */
final class MavenJfrRuntimeCauseExplainer {
    private static final int MAX_CONSOLE_EXPLANATIONS = 5;
    private static final int MAX_CONSOLE_SIGNALS = 2;

    List<String> consoleLines(MavenJfrTestAttribution.Summary attribution) {
        List<Explanation> explanations = new ArrayList<>();
        attribution.evidence().stream()
                .map(evidence -> explanation(
                        evidence.testIdentity(), "test body", evidence.eventDurations(), evidence.eventLocations(), evidence.directDuration()
                ))
                .flatMap(java.util.Optional::stream)
                .forEach(explanations::add);
        attribution.phaseEvidence().stream()
                .map(evidence -> explanation(
                        evidence.testIdentity(), evidence.phase(), evidence.eventDurations(), evidence.eventLocations(), evidence.directDuration()
                ))
                .flatMap(java.util.Optional::stream)
                .forEach(explanations::add);
        attribution.samples().stream()
                .map(evidence -> sampledExplanation(
                        evidence.testIdentity(), "test body", evidence.sampleCounts(), evidence.sampleLocations()
                ))
                .flatMap(java.util.Optional::stream)
                .forEach(explanations::add);
        attribution.phaseSamples().stream()
                .map(evidence -> sampledExplanation(
                        evidence.testIdentity(), evidence.phase(), evidence.sampleCounts(), evidence.sampleLocations()
                ))
                .flatMap(java.util.Optional::stream)
                .forEach(explanations::add);
        List<Explanation> measuredExplanations = explanations.stream()
                .filter(explanation -> !explanation.sampled())
                .sorted(Comparator.comparing(Explanation::duration).reversed())
                .toList();
        List<Explanation> sampledExplanations = explanations.stream()
                .filter(Explanation::sampled)
                .sorted(Comparator.comparing(Explanation::duration).reversed())
                .toList();
        List<String> lines = measuredExplanations.stream()
                .limit(MAX_CONSOLE_EXPLANATIONS)
                .map(Explanation::consoleLine)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (measuredExplanations.size() > MAX_CONSOLE_EXPLANATIONS) {
            lines.add("[TestSleuth] JFR cause explanations: " + MAX_CONSOLE_EXPLANATIONS + " shown, "
                    + (measuredExplanations.size() - MAX_CONSOLE_EXPLANATIONS) + " additional directly owned causes available in raw evidence");
        }
        sampledExplanations.stream()
                .limit(MAX_CONSOLE_SIGNALS)
                .map(Explanation::consoleLine)
                .forEach(lines::add);
        if (sampledExplanations.size() > MAX_CONSOLE_SIGNALS) {
            lines.add("[TestSleuth] JFR sampled signals: " + MAX_CONSOLE_SIGNALS + " shown, "
                    + (sampledExplanations.size() - MAX_CONSOLE_SIGNALS) + " additional directly owned samples available in raw evidence");
        }
        return List.copyOf(lines);
    }

    private static java.util.Optional<Explanation> explanation(
            String testIdentity,
            String location,
            Map<String, Duration> durations,
            Map<String, String> eventLocations,
            Duration totalDuration
    ) {
        return durations.entrySet().stream()
                .max(Comparator.comparing(Map.Entry::getValue))
                .flatMap(entry -> cause(entry.getKey()).map(cause -> java.util.Optional.of(new Explanation(
                        testIdentity, location, cause, entry.getKey(), entry.getValue(), eventLocations.getOrDefault(entry.getKey(), ""), totalDuration
                ))).orElseGet(java.util.Optional::empty));
    }

    private static java.util.Optional<Cause> cause(String eventFamily) {
        return switch (eventFamily) {
            case "ThreadSleep" -> java.util.Optional.of(new Cause(
                    "fixed wait", "replace the fixed delay with a condition-driven wait or mock the boundary"
            ));
            case "ThreadPark" -> java.util.Optional.of(new Cause(
                    "parked or polling wait", "inspect executor, retry, and polling behavior; await a deterministic completion signal"
            ));
            case "SocketRead", "SocketWrite" -> java.util.Optional.of(new Cause(
                    "external I/O", "stub or virtualize the network boundary, or narrow the integration scope"
            ));
            case "MonitorEnter" -> java.util.Optional.of(new Cause(
                    "lock contention", "remove shared mutable fixtures or reduce synchronization on the test path"
            ));
            case "FileRead", "FileWrite" -> java.util.Optional.of(new Cause(
                    "file I/O", "reuse fixtures or replace disk access with a focused in-memory test seam"
            ));
            case "ClassLoad" -> java.util.Optional.of(new Cause(
                    "class loading and warm-up", "move one-time initialization out of repeated test paths and compare first-test versus steady-state cost"
            ));
            default -> java.util.Optional.empty();
        };
    }

    private static java.util.Optional<Explanation> sampledExplanation(
            String testIdentity,
            String location,
            Map<String, Long> counts,
            Map<String, String> locations
    ) {
        return counts.entrySet().stream()
                .max(Comparator.comparing(Map.Entry::getValue))
                .flatMap(entry -> sampledCause(entry.getKey()).map(cause -> java.util.Optional.of(new Explanation(
                        testIdentity, location, cause, entry.getKey(), entry.getValue(), locations.getOrDefault(entry.getKey(), "")
                ))).orElseGet(java.util.Optional::empty));
    }

    private static java.util.Optional<SampledCause> sampledCause(String eventFamily) {
        return switch (eventFamily) {
            case "ExecutionSample" -> java.util.Optional.of(new SampledCause(
                    "CPU activity", "inspect the sampled stack and reduce repeated computation, fixture construction, serialization, or reflection"
            ));
            case "ObjectAllocationSample" -> java.util.Optional.of(new SampledCause(
                    "allocation activity", "inspect the sampled stack and reuse large fixtures or avoid repeated object construction"
            ));
            default -> java.util.Optional.empty();
        };
    }

    private record Cause(String category, String action) {
    }

    private record SampledCause(String category, String action) {
    }

    private record Explanation(
            String testIdentity,
            String location,
            Cause cause,
            String eventFamily,
            Duration duration,
            String stackLocation,
            Duration totalDuration
    ) {
        private String consoleLine() {
            if (totalDuration.isZero()) {
                return "[TestSleuth] JFR signal: " + testIdentity + " " + location + " " + cause.category
                        + " - " + eventFamily + " " + duration.toMillis()
                        + " samples (sampled activity, not elapsed time); action: " + cause.action
                        + (stackLocation.isBlank() ? "" : "; stack: " + stackLocation);
            }
            long percentage = totalDuration.isZero() ? 0 : Math.round(duration.toMillis() * 100.0 / totalDuration.toMillis());
            return "[TestSleuth] JFR cause: " + testIdentity + " " + location + " " + cause.category
                    + " - " + eventFamily + " " + duration.toMillis() + " ms (" + percentage + "% of direct evidence); action: "
                    + cause.action + (stackLocation.isBlank() ? "" : "; stack: " + stackLocation);
        }

        private boolean sampled() {
            return totalDuration.isZero();
        }

        private Explanation(
                String testIdentity,
                String location,
                SampledCause cause,
                String eventFamily,
                long count,
                String stackLocation
        ) {
            this(testIdentity, location, new Cause(cause.category, cause.action), eventFamily,
                    Duration.ofMillis(count), stackLocation, Duration.ZERO);
        }
    }
}

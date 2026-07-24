package dev.testsleuth.maven;

import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.RuntimeWaitEventAttributes;
import dev.testsleuth.core.event.TestSleuthEvent;
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
import java.util.Optional;

final class MavenRuntimeWaitFindings {
    private final TestSleuthMavenConfig config;

    MavenRuntimeWaitFindings(TestSleuthMavenConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    List<Finding> detect(List<TestSleuthEvent> events) {
        Objects.requireNonNull(events, "events");
        if (!config.runtimeWaitsEnabled() || config.maxFindings() == 0) {
            return List.of();
        }

        return events.stream()
                .filter(event -> event.kind() == EventKind.WAIT_FINISHED)
                .filter(event -> RuntimeWaitEventAttributes.COLLECTOR.equals(event.attributes().get("collector")))
                .map(RuntimeWaitObservation::from)
                .flatMap(Optional::stream)
                .filter(observation -> observation.duration().compareTo(config.fixedWaitThreshold()) >= 0)
                .sorted(Comparator.comparing(RuntimeWaitObservation::duration).reversed()
                        .thenComparing(RuntimeWaitObservation::operation)
                        .thenComparing(RuntimeWaitObservation::eventId))
                .limit(config.maxFindings())
                .map(this::toFinding)
                .toList();
    }

    private Finding toFinding(RuntimeWaitObservation observation) {
        return new Finding(
                new FindingId("runtime-wait:" + sanitize(observation.moduleId()) + ":" + sanitize(observation.eventId())),
                "Runtime wait observed: " + observation.operation(),
                FindingCategory.WAITING,
                severity(observation.duration()),
                Confidence.HIGH,
                EvidenceType.MEASURED,
                AttributionScope.FORK_WIDE,
                observation.duration(),
                new TimeSavingEstimate(Duration.ZERO, observation.duration()),
                List.of(observation.operation()),
                evidence(observation),
                "A wait operation executed during the test run and consumed observed wall-clock time.",
                "Replace fixed or timeout-based waiting with condition-based synchronization, callbacks, fakes, or shorter bounded waits.",
                "Measured runtime finding, but the current collector does not yet associate the event with a specific test window.",
                "Rerun TestSleuth and confirm the runtime wait duration falls; JFR attribution will later identify the affected test or phase."
        );
    }

    private static List<String> evidence(RuntimeWaitObservation observation) {
        java.util.ArrayList<String> evidence = new java.util.ArrayList<>();
        evidence.add(observation.operation() + " observed " + observation.duration().toMillis() + " ms at runtime.");
        observation.configuredTimeout().ifPresent(timeout ->
                evidence.add("Configured timeout: " + timeout.toMillis() + " ms."));
        observation.outcome().ifPresent(outcome -> evidence.add("Outcome: " + outcome + "."));
        observation.threadName().ifPresent(thread -> evidence.add("Thread: " + thread + "."));
        observation.collectorOverhead().ifPresent(overhead ->
                evidence.add("Collector overhead: " + overhead.toNanos() + " ns."));
        evidence.add("Detector: runtime-waits.");
        evidence.add("Module: " + observation.moduleId() + ".");
        observation.buildRunId().ifPresent(buildRun -> evidence.add("Build run: " + buildRun + "."));
        observation.processId().ifPresent(process -> evidence.add("Process IDs: " + process + "."));
        observation.forkNumber().ifPresent(fork -> evidence.add("Fork numbers: " + fork + "."));
        return List.copyOf(evidence);
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

    private static Optional<Duration> durationAttribute(TestSleuthEvent event, String attributeName) {
        return Optional.ofNullable(event.attributes().get(attributeName))
                .flatMap(MavenRuntimeWaitFindings::parseLong)
                .map(Duration::ofMillis);
    }

    private static Optional<Duration> overheadAttribute(TestSleuthEvent event) {
        return Optional.ofNullable(event.attributes().get(RuntimeWaitEventAttributes.COLLECTOR_OVERHEAD_NANOS))
                .flatMap(MavenRuntimeWaitFindings::parseLong)
                .map(Duration::ofNanos);
    }

    private static Optional<Long> parseLong(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String attribute(TestSleuthEvent event, String attributeName, String fallback) {
        String value = event.attributes().get(attributeName);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static Optional<String> optionalAttribute(TestSleuthEvent event, String attributeName) {
        String value = event.attributes().get(attributeName);
        if (value == null || value.isBlank() || "unknown".equals(value)) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._:-]", "_");
    }

    private record RuntimeWaitObservation(
            String eventId,
            String operation,
            Duration duration,
            Optional<Duration> configuredTimeout,
            Optional<String> outcome,
            Optional<String> threadName,
            Optional<Duration> collectorOverhead,
            String moduleId,
            Optional<String> buildRunId,
            Optional<String> processId,
            Optional<String> forkNumber
    ) {
        private static Optional<RuntimeWaitObservation> from(TestSleuthEvent event) {
            Optional<Duration> duration = durationAttribute(event, RuntimeWaitEventAttributes.OBSERVED_DURATION_MILLIS);
            if (duration.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new RuntimeWaitObservation(
                    event.id().value(),
                    attribute(event, RuntimeWaitEventAttributes.OPERATION, event.subject().identifier()),
                    duration.get(),
                    durationAttribute(event, RuntimeWaitEventAttributes.CONFIGURED_TIMEOUT_MILLIS),
                    optionalAttribute(event, RuntimeWaitEventAttributes.OUTCOME),
                    optionalAttribute(event, RuntimeWaitEventAttributes.THREAD_NAME),
                    overheadAttribute(event),
                    attribute(event, "moduleId", "unknown"),
                    optionalAttribute(event, "buildRunId"),
                    optionalAttribute(event, "processId"),
                    optionalAttribute(event, "forkNumber")
            ));
        }
    }
}

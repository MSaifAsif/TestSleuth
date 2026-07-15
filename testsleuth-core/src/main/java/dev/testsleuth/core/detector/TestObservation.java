package dev.testsleuth.core.detector;

import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.TestSleuthEvent;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record TestObservation(
        String buildRunId,
        String moduleId,
        String testIdentity,
        List<TestSleuthEvent> events
) {
    public TestObservation {
        buildRunId = normalize(buildRunId);
        moduleId = normalize(moduleId);
        testIdentity = normalize(testIdentity);
        events = List.copyOf(Objects.requireNonNull(events, "events"));
    }

    Optional<TestSleuthEvent> selectedFinishedEvent(Optional<String> preferredCollector) {
        Objects.requireNonNull(preferredCollector, "preferredCollector");
        if (preferredCollector.isPresent()) {
            return finishedEventFromCollector(preferredCollector.get());
        }
        return events.stream()
                .filter(TestObservation::isPassedFinishedEvent)
                .max(Comparator.comparing(TestObservation::durationMillis));
    }

    List<String> collectors() {
        return events.stream()
                .map(event -> event.attributes().getOrDefault("collector", "unknown"))
                .distinct()
                .sorted()
                .toList();
    }

    Optional<String> firstAttribute(String name) {
        Objects.requireNonNull(name, "name");
        return events.stream()
                .map(event -> event.attributes().get(name))
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .findFirst();
    }

    List<String> distinctAttributeValues(String name) {
        Objects.requireNonNull(name, "name");
        return events.stream()
                .map(event -> event.attributes().get(name))
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
    }

    private Optional<TestSleuthEvent> finishedEventFromCollector(String collector) {
        return events.stream()
                .filter(TestObservation::isPassedFinishedEvent)
                .filter(event -> collector.equals(event.attributes().get("collector")))
                .max(Comparator.comparing(TestObservation::durationMillis));
    }

    static boolean isPassedFinishedEvent(TestSleuthEvent event) {
        return event.kind() == EventKind.TEST_FINISHED
                && "passed".equals(event.attributes().get("status"))
                && event.attributes().containsKey("durationMillis");
    }

    static Duration duration(TestSleuthEvent event) {
        return Duration.ofMillis(durationMillis(event));
    }

    private static long durationMillis(TestSleuthEvent event) {
        try {
            return Long.parseLong(event.attributes().getOrDefault("durationMillis", "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim();
    }
}

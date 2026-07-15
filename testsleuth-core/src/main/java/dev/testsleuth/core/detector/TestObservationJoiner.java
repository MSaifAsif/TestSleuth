package dev.testsleuth.core.detector;

import dev.testsleuth.core.event.SubjectType;
import dev.testsleuth.core.event.TestSleuthEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TestObservationJoiner {
    public List<TestObservation> join(List<TestSleuthEvent> events) {
        Objects.requireNonNull(events, "events");

        Map<ObservationKey, List<TestSleuthEvent>> groups = new LinkedHashMap<>();
        for (TestSleuthEvent event : events) {
            if (event.subject().type() != SubjectType.TEST_METHOD) {
                continue;
            }
            groups.computeIfAbsent(ObservationKey.from(event), ignored -> new ArrayList<>()).add(event);
        }

        return groups.entrySet().stream()
                .map(entry -> new TestObservation(
                        entry.getKey().buildRunId(),
                        entry.getKey().moduleId(),
                        entry.getKey().testIdentity(),
                        entry.getValue()
                ))
                .sorted(Comparator
                        .comparing(TestObservation::buildRunId)
                        .thenComparing(TestObservation::moduleId)
                        .thenComparing(TestObservation::testIdentity))
                .toList();
    }

    private record ObservationKey(String buildRunId, String moduleId, String testIdentity) {
        private static ObservationKey from(TestSleuthEvent event) {
            return new ObservationKey(
                    attribute(event, "buildRunId", "unknown"),
                    attribute(event, "moduleId", "unknown"),
                    event.attributes().getOrDefault("testIdentity", event.subject().identifier())
            );
        }

        private static String attribute(TestSleuthEvent event, String name, String fallback) {
            String value = event.attributes().get(name);
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return value.trim();
        }
    }
}

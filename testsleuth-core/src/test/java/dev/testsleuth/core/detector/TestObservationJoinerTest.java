package dev.testsleuth.core.detector;

import dev.testsleuth.core.event.EventId;
import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.Subject;
import dev.testsleuth.core.event.SubjectType;
import dev.testsleuth.core.event.TestSleuthEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TestObservationJoinerTest {
    @Test
    void joinsEventsByRunModuleAndTestIdentity() {
        List<TestObservation> observations = new TestObservationJoiner().join(List.of(
                event("run-1", "module-a", "ExampleTest.slow", "junit5-listener", EventKind.TEST_STARTED, 0),
                event("run-1", "module-a", "ExampleTest.slow", "junit5-listener", EventKind.TEST_FINISHED, 200),
                event("run-1", "module-a", "ExampleTest.slow", "maven-test-report", EventKind.TEST_FINISHED, 210),
                event("run-1", "module-b", "ExampleTest.slow", "maven-test-report", EventKind.TEST_FINISHED, 300)
        ));

        assertEquals(2, observations.size());
        TestObservation first = observations.get(0);
        assertEquals("run-1", first.buildRunId());
        assertEquals("module-a", first.moduleId());
        assertEquals("ExampleTest.slow", first.testIdentity());
        assertEquals(3, first.events().size());
        assertEquals(List.of("junit5-listener", "maven-test-report"), first.collectors());
    }

    private static TestSleuthEvent event(
            String buildRunId,
            String moduleId,
            String testIdentity,
            String collector,
            EventKind kind,
            long durationMillis
    ) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("buildRunId", buildRunId);
        attributes.put("moduleId", moduleId);
        attributes.put("testIdentity", testIdentity);
        attributes.put("collector", collector);
        if (kind == EventKind.TEST_FINISHED) {
            attributes.put("status", "passed");
            attributes.put("durationMillis", Long.toString(durationMillis));
        }

        return new TestSleuthEvent(
                new EventId(collector + "-" + moduleId + "-" + kind.name()),
                Optional.empty(),
                kind,
                new Subject(SubjectType.TEST_METHOD, testIdentity),
                Instant.EPOCH,
                0,
                attributes
        );
    }
}

package dev.testsleuth.core.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class EventJsonReaderTest {
    @Test
    void readsEventsWrittenByEventJsonWriter() {
        TestSleuthEvent event = new TestSleuthEvent(
                new EventId("event-1"),
                Optional.of(new EventId("parent-1")),
                EventKind.TEST_FINISHED,
                new Subject(SubjectType.TEST_METHOD, "ExampleTest.succeeds"),
                Instant.parse("2026-07-14T10:15:30Z"),
                123,
                Map.of("status", "passed", "displayName", "handles \"quotes\"")
        );

        String json = new EventJsonWriter().write(List.of(event));
        List<TestSleuthEvent> events = new EventJsonReader().read(json);

        assertEquals(1, events.size());
        TestSleuthEvent read = events.get(0);
        assertEquals("event-1", read.id().value());
        assertEquals("parent-1", read.parentId().orElseThrow().value());
        assertEquals(EventKind.TEST_FINISHED, read.kind());
        assertEquals(SubjectType.TEST_METHOD, read.subject().type());
        assertEquals("ExampleTest.succeeds", read.subject().identifier());
        assertEquals("handles \"quotes\"", read.attributes().get("displayName"));
    }
}

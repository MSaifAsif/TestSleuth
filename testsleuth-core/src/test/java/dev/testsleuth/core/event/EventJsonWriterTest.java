package dev.testsleuth.core.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class EventJsonWriterTest {
    @Test
    void writesStableJsonForEvents() {
        TestSleuthEvent event = new TestSleuthEvent(
                new EventId("event-1"),
                Optional.of(new EventId("parent-1")),
                EventKind.TEST_FINISHED,
                new Subject(SubjectType.TEST_METHOD, "ExampleTest.succeeds"),
                Instant.parse("2026-07-14T10:15:30Z"),
                123,
                Map.of("status", "passed", "displayName", "handles \"quotes\"")
        );

        String json = new EventJsonWriter().write(java.util.List.of(event));

        assertTrue(json.contains("\"id\":\"event-1\""));
        assertTrue(json.contains("\"parentId\":\"parent-1\""));
        assertTrue(json.contains("\"kind\":\"TEST_FINISHED\""));
        assertTrue(json.contains("\"displayName\":\"handles \\\"quotes\\\"\""));
        assertTrue(json.endsWith("\n]\n"));
    }
}


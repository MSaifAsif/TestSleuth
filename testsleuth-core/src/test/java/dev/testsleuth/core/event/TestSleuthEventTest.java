package dev.testsleuth.core.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TestSleuthEventTest {
    @Test
    void rejectsNegativeMonotonicTime() {
        EventId id = new EventId("event-1");
        Subject subject = new Subject(SubjectType.BUILD, "root");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new TestSleuthEvent(
                        id,
                        Optional.empty(),
                        EventKind.BUILD_STARTED,
                        subject,
                        Instant.EPOCH,
                        -1,
                        Map.of()
                )
        );

        assertEquals("monotonic nanos must not be negative", exception.getMessage());
    }

    @Test
    void defensivelyCopiesAttributes() {
        TestSleuthEvent event = new TestSleuthEvent(
                new EventId("event-1"),
                Optional.empty(),
                EventKind.BUILD_STARTED,
                new Subject(SubjectType.BUILD, "root"),
                Instant.EPOCH,
                1,
                Map.of("module", "root")
        );

        assertEquals("root", event.attributes().get("module"));
        assertThrows(UnsupportedOperationException.class, () -> event.attributes().put("other", "value"));
    }
}


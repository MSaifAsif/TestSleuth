package dev.testsleuth.core.event;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RuntimeWaitEventAttributesTest {
    @Test
    void createsRuntimeWaitFinishedAttributes() {
        Map<String, String> attributes = RuntimeWaitEventAttributes.finished(
                "Thread.sleep",
                Duration.ofMillis(125),
                Optional.of(Duration.ofMillis(250)),
                RuntimeWaitEventAttributes.WaitOutcome.COMPLETED,
                Thread.currentThread()
        );

        assertEquals(RuntimeWaitEventAttributes.COLLECTOR, attributes.get("collector"));
        assertEquals("Thread.sleep", attributes.get(RuntimeWaitEventAttributes.OPERATION));
        assertEquals("125", attributes.get(RuntimeWaitEventAttributes.OBSERVED_DURATION_MILLIS));
        assertEquals("250", attributes.get(RuntimeWaitEventAttributes.CONFIGURED_TIMEOUT_MILLIS));
        assertEquals("completed", attributes.get(RuntimeWaitEventAttributes.OUTCOME));
        assertEquals(Thread.currentThread().getName(), attributes.get(RuntimeWaitEventAttributes.THREAD_NAME));
    }

    @Test
    void runtimeWaitEventsRoundTripThroughJson() {
        TestSleuthEvent event = new TestSleuthEvent(
                new EventId("runtime-wait-1"),
                Optional.empty(),
                EventKind.WAIT_FINISHED,
                new Subject(SubjectType.TEST_METHOD, "ExampleTest.waits"),
                Instant.parse("2026-07-16T00:00:00Z"),
                123,
                RuntimeWaitEventAttributes.finished(
                        "CountDownLatch.await",
                        Duration.ofMillis(75),
                        Optional.of(Duration.ofSeconds(1)),
                        RuntimeWaitEventAttributes.WaitOutcome.TIMED_OUT,
                        Thread.currentThread()
                )
        );

        List<TestSleuthEvent> read = new EventJsonReader().read(new EventJsonWriter().write(List.of(event)));

        assertEquals(1, read.size());
        assertEquals(EventKind.WAIT_FINISHED, read.get(0).kind());
        assertEquals("CountDownLatch.await", read.get(0).attributes().get(RuntimeWaitEventAttributes.OPERATION));
        assertEquals("timed-out", read.get(0).attributes().get(RuntimeWaitEventAttributes.OUTCOME));
        assertEquals("1000", read.get(0).attributes().get(RuntimeWaitEventAttributes.CONFIGURED_TIMEOUT_MILLIS));
    }

    @Test
    void rejectsInvalidWaitAttributeValues() {
        assertThrows(IllegalArgumentException.class, () -> RuntimeWaitEventAttributes.finished(
                "",
                Duration.ZERO,
                Optional.empty(),
                RuntimeWaitEventAttributes.WaitOutcome.COMPLETED,
                Thread.currentThread()
        ));
        assertThrows(IllegalArgumentException.class, () -> RuntimeWaitEventAttributes.finished(
                "Thread.sleep",
                Duration.ofMillis(-1),
                Optional.empty(),
                RuntimeWaitEventAttributes.WaitOutcome.COMPLETED,
                Thread.currentThread()
        ));
    }
}

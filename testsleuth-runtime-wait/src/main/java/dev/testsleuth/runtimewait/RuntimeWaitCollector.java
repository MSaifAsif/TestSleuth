package dev.testsleuth.runtimewait;

import dev.testsleuth.core.event.EventJsonReader;
import dev.testsleuth.core.event.EventJsonWriter;
import dev.testsleuth.core.event.RuntimeWaitEventAttributes;
import dev.testsleuth.core.event.TestSleuthEvent;
import dev.testsleuth.core.event.TestSleuthRunContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public final class RuntimeWaitCollector {
    private final RuntimeWaitEventFactory eventFactory;
    private final List<TestSleuthEvent> events = new ArrayList<>();

    public RuntimeWaitCollector() {
        this(TestSleuthRunContext.fromSystemProperties());
    }

    public RuntimeWaitCollector(TestSleuthRunContext runContext) {
        this.eventFactory = new RuntimeWaitEventFactory(Objects.requireNonNull(runContext, "runContext"));
    }

    public void sleep(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        validateTimeout(timeout);

        long beforeCollectorNanos = System.nanoTime();
        Thread thread = Thread.currentThread();
        Instant wallClockTime = Instant.now();
        long waitStartedNanos = System.nanoTime();
        RuntimeWaitEventAttributes.WaitOutcome outcome = RuntimeWaitEventAttributes.WaitOutcome.COMPLETED;
        try {
            Thread.sleep(timeout.toMillis(), nanosAdjustment(timeout));
        } catch (InterruptedException e) {
            outcome = RuntimeWaitEventAttributes.WaitOutcome.INTERRUPTED;
            throw e;
        } finally {
            long finishedNanos = System.nanoTime();
            record(
                    RuntimeWaitOperations.THREAD_SLEEP,
                    elapsed(waitStartedNanos, finishedNanos),
                    Optional.of(timeout),
                    outcome,
                    thread,
                    wallClockTime,
                    waitStartedNanos,
                    collectorOverhead(beforeCollectorNanos, waitStartedNanos, finishedNanos)
            );
        }
    }

    public void waitOn(Object monitor, Duration timeout) throws InterruptedException {
        Objects.requireNonNull(monitor, "monitor");
        Objects.requireNonNull(timeout, "timeout");
        validateTimeout(timeout);

        long beforeCollectorNanos = System.nanoTime();
        Thread thread = Thread.currentThread();
        Instant wallClockTime = Instant.now();
        long waitStartedNanos = System.nanoTime();
        RuntimeWaitEventAttributes.WaitOutcome outcome = RuntimeWaitEventAttributes.WaitOutcome.TIMED_OUT;
        try {
            monitor.wait(timeout.toMillis(), nanosAdjustment(timeout));
        } catch (InterruptedException e) {
            outcome = RuntimeWaitEventAttributes.WaitOutcome.INTERRUPTED;
            throw e;
        } catch (RuntimeException e) {
            outcome = RuntimeWaitEventAttributes.WaitOutcome.FAILED;
            throw e;
        } finally {
            long finishedNanos = System.nanoTime();
            record(
                    RuntimeWaitOperations.OBJECT_WAIT,
                    elapsed(waitStartedNanos, finishedNanos),
                    Optional.of(timeout),
                    outcome,
                    thread,
                    wallClockTime,
                    waitStartedNanos,
                    collectorOverhead(beforeCollectorNanos, waitStartedNanos, finishedNanos)
            );
        }
    }

    public void parkNanos(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        validateTimeout(timeout);

        long beforeCollectorNanos = System.nanoTime();
        Thread thread = Thread.currentThread();
        Instant wallClockTime = Instant.now();
        long waitStartedNanos = System.nanoTime();
        LockSupport.parkNanos(timeout.toNanos());
        long finishedNanos = System.nanoTime();
        RuntimeWaitEventAttributes.WaitOutcome outcome = thread.isInterrupted()
                ? RuntimeWaitEventAttributes.WaitOutcome.INTERRUPTED
                : RuntimeWaitEventAttributes.WaitOutcome.TIMED_OUT;
        record(
                RuntimeWaitOperations.LOCK_SUPPORT_PARK_NANOS,
                elapsed(waitStartedNanos, finishedNanos),
                Optional.of(timeout),
                outcome,
                thread,
                wallClockTime,
                waitStartedNanos,
                collectorOverhead(beforeCollectorNanos, waitStartedNanos, finishedNanos)
        );
    }

    public void parkUntil(Instant deadline) {
        Objects.requireNonNull(deadline, "deadline");

        long beforeCollectorNanos = System.nanoTime();
        Thread thread = Thread.currentThread();
        Instant wallClockTime = Instant.now();
        Optional<Duration> configuredTimeout = configuredTimeoutUntil(deadline, wallClockTime);
        long waitStartedNanos = System.nanoTime();
        LockSupport.parkUntil(deadline.toEpochMilli());
        long finishedNanos = System.nanoTime();
        RuntimeWaitEventAttributes.WaitOutcome outcome = thread.isInterrupted()
                ? RuntimeWaitEventAttributes.WaitOutcome.INTERRUPTED
                : RuntimeWaitEventAttributes.WaitOutcome.TIMED_OUT;
        record(
                RuntimeWaitOperations.LOCK_SUPPORT_PARK_UNTIL,
                elapsed(waitStartedNanos, finishedNanos),
                configuredTimeout,
                outcome,
                thread,
                wallClockTime,
                waitStartedNanos,
                collectorOverhead(beforeCollectorNanos, waitStartedNanos, finishedNanos)
        );
    }

    public List<TestSleuthEvent> events() {
        return List.copyOf(events);
    }

    public void writeTo(Path eventsFile, Consumer<String> failureReporter) {
        Objects.requireNonNull(eventsFile, "eventsFile");
        Objects.requireNonNull(failureReporter, "failureReporter");

        try {
            Path parent = eventsFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(eventsFile, new EventJsonWriter().write(mergedEvents(eventsFile)), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            failureReporter.accept("Failed to write runtime wait events to " + eventsFile + ": " + e.getMessage());
        }
    }

    private List<TestSleuthEvent> mergedEvents(Path eventsFile) throws IOException {
        if (!Files.isRegularFile(eventsFile)) {
            return events();
        }

        List<TestSleuthEvent> merged = new ArrayList<>(new EventJsonReader().read(Files.readString(
                eventsFile,
                StandardCharsets.UTF_8
        )));
        merged.addAll(events);
        return List.copyOf(merged);
    }

    private void record(
            String operation,
            Duration observedDuration,
            Optional<Duration> configuredTimeout,
            RuntimeWaitEventAttributes.WaitOutcome outcome,
            Thread thread,
            Instant wallClockTime,
            long monotonicNanos,
            long collectorOverheadNanos
    ) {
        events.add(eventFactory.finishedEvent(new RuntimeWaitObservation(
                operation,
                observedDuration,
                configuredTimeout,
                outcome,
                thread,
                wallClockTime,
                monotonicNanos,
                collectorOverheadNanos,
                Optional.empty(),
                Optional.empty()
        )));
    }

    private static Optional<Duration> configuredTimeoutUntil(Instant deadline, Instant now) {
        if (!deadline.isAfter(now)) {
            return Optional.of(Duration.ZERO);
        }
        return Optional.of(Duration.between(now, deadline));
    }

    private static Duration elapsed(long startedNanos, long finishedNanos) {
        return Duration.ofNanos(Math.max(0, finishedNanos - startedNanos));
    }

    private static long collectorOverhead(long beforeCollectorNanos, long waitStartedNanos, long finishedNanos) {
        long beforeWaitOverhead = Math.max(0, waitStartedNanos - beforeCollectorNanos);
        long afterWaitOverhead = Math.max(0, System.nanoTime() - finishedNanos);
        return beforeWaitOverhead + afterWaitOverhead;
    }

    private static void validateTimeout(Duration timeout) {
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
    }

    private static int nanosAdjustment(Duration timeout) {
        return timeout.minusMillis(timeout.toMillis()).toNanosPart();
    }
}

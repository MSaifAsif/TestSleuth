package dev.testsleuth.runtimewait;

import dev.testsleuth.core.event.EventId;
import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.RuntimeWaitEventAttributes;
import dev.testsleuth.core.event.Subject;
import dev.testsleuth.core.event.SubjectType;
import dev.testsleuth.core.event.TestSleuthEvent;
import dev.testsleuth.core.event.TestSleuthRunContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RuntimeWaitEventFactory {
    private final TestSleuthRunContext runContext;

    public RuntimeWaitEventFactory(TestSleuthRunContext runContext) {
        this.runContext = Objects.requireNonNull(runContext, "runContext");
    }

    public TestSleuthEvent finishedEvent(RuntimeWaitObservation observation) {
        Objects.requireNonNull(observation, "observation");
        return new TestSleuthEvent(
                EventId.random(),
                Optional.empty(),
                EventKind.WAIT_FINISHED,
                new Subject(SubjectType.WAIT, observation.operation()),
                observation.wallClockTime(),
                observation.monotonicNanos(),
                attributes(observation)
        );
    }

    private Map<String, String> attributes(RuntimeWaitObservation observation) {
        Map<String, String> attributes = new LinkedHashMap<>(RuntimeWaitEventAttributes.finished(
                observation.operation(),
                observation.observedDuration(),
                observation.configuredTimeout(),
                observation.outcome(),
                observation.thread()
        ));
        attributes.putAll(runContext.attributes());
        attributes.put(
                RuntimeWaitEventAttributes.COLLECTOR_OVERHEAD_NANOS,
                Long.toString(observation.collectorOverheadNanos())
        );
        observation.sourceLocation()
                .ifPresent(value -> attributes.put(RuntimeWaitEventAttributes.SOURCE_LOCATION, value));
        observation.stackTop()
                .ifPresent(value -> attributes.put(RuntimeWaitEventAttributes.STACK_TOP, value));
        return Map.copyOf(attributes);
    }
}

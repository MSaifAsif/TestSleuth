package dev.testsleuth.junit5;

import dev.testsleuth.core.event.EventId;
import dev.testsleuth.core.event.EventJsonWriter;
import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.Subject;
import dev.testsleuth.core.event.SubjectType;
import dev.testsleuth.core.event.TestSleuthRunContext;
import dev.testsleuth.core.event.TestSleuthEvent;
import dev.testsleuth.core.event.TestSubjectIdentity;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class JUnitLifecycleEventCollector {
    private final List<TestSleuthEvent> events = new ArrayList<>();
    private final Map<String, Long> startedNanos = new HashMap<>();
    private final TestSleuthRunContext runContext;

    public JUnitLifecycleEventCollector() {
        this(TestSleuthRunContext.fromSystemProperties());
    }

    JUnitLifecycleEventCollector(TestSleuthRunContext runContext) {
        this.runContext = Objects.requireNonNull(runContext, "runContext");
    }

    public void recordDiscoveredTests(TestPlan testPlan) {
        Objects.requireNonNull(testPlan, "testPlan");

        for (TestIdentifier root : testPlan.getRoots()) {
            for (TestIdentifier testIdentifier : testPlan.getDescendants(root)) {
                if (testIdentifier.isTest()) {
                    events.add(toEvent(EventKind.TEST_DISCOVERED, testIdentifier, Map.of()));
                }
            }
        }
    }

    public void recordStarted(TestIdentifier testIdentifier) {
        Objects.requireNonNull(testIdentifier, "testIdentifier");

        if (!testIdentifier.isTest()) {
            return;
        }

        long now = System.nanoTime();
        startedNanos.put(testIdentifier.getUniqueId(), now);
        events.add(toEvent(EventKind.TEST_STARTED, testIdentifier, Map.of()));
    }

    public void recordFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        Objects.requireNonNull(testIdentifier, "testIdentifier");
        Objects.requireNonNull(testExecutionResult, "testExecutionResult");

        if (!testIdentifier.isTest()) {
            return;
        }

        long now = System.nanoTime();
        long started = startedNanos.getOrDefault(testIdentifier.getUniqueId(), now);
        long durationMillis = Math.max(0, Math.round((now - started) / 1_000_000.0));

        Map<String, String> attributes = new HashMap<>();
        attributes.put("status", status(testExecutionResult));
        attributes.put("durationMillis", Long.toString(durationMillis));
        testExecutionResult.getThrowable()
                .map(throwable -> throwable.getClass().getName())
                .ifPresent(value -> attributes.put("throwableType", value));

        events.add(toEvent(EventKind.TEST_FINISHED, testIdentifier, attributes));
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
            Files.writeString(eventsFile, new EventJsonWriter().write(events), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            failureReporter.accept("Failed to write JUnit lifecycle events to " + eventsFile + ": " + e.getMessage());
        }
    }

    private TestSleuthEvent toEvent(EventKind kind, TestIdentifier testIdentifier, Map<String, String> attributes) {
        Map<String, String> eventAttributes = new HashMap<>();
        eventAttributes.putAll(runContext.attributes());
        eventAttributes.put("collector", "junit5-listener");
        eventAttributes.put("uniqueId", testIdentifier.getUniqueId());
        eventAttributes.put("displayName", testIdentifier.getDisplayName());
        testIdentifier.getSource().ifPresent(source -> eventAttributes.put("source", source.toString()));
        methodSource(testIdentifier).ifPresent(methodSource -> {
            eventAttributes.put("className", methodSource.getClassName());
            eventAttributes.put("methodName", methodSource.getMethodName());
            eventAttributes.put("testIdentity", TestSubjectIdentity.testMethod(
                    methodSource.getClassName(),
                    methodSource.getMethodName()
            ));
        });
        eventAttributes.putAll(attributes);

        return new TestSleuthEvent(
                new EventId("junit5:" + kind.name() + ":" + safeId(testIdentifier.getUniqueId())),
                Optional.empty(),
                kind,
                new Subject(subjectType(testIdentifier), subjectIdentifier(testIdentifier)),
                Instant.now(),
                System.nanoTime(),
                eventAttributes
        );
    }

    private static String subjectIdentifier(TestIdentifier testIdentifier) {
        return methodSource(testIdentifier)
                .map(source -> TestSubjectIdentity.testMethod(source.getClassName(), source.getMethodName()))
                .orElseGet(testIdentifier::getDisplayName);
    }

    private static Optional<MethodSource> methodSource(TestIdentifier testIdentifier) {
        return testIdentifier.getSource()
                .filter(MethodSource.class::isInstance)
                .map(MethodSource.class::cast);
    }

    private static SubjectType subjectType(TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            return SubjectType.TEST_METHOD;
        }
        if (testIdentifier.isContainer()) {
            return SubjectType.SUITE;
        }
        return SubjectType.TEST_METHOD;
    }

    private static String status(TestExecutionResult testExecutionResult) {
        return switch (testExecutionResult.getStatus()) {
            case SUCCESSFUL -> "passed";
            case ABORTED -> "skipped";
            case FAILED -> "failed";
        };
    }

    private static String safeId(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}

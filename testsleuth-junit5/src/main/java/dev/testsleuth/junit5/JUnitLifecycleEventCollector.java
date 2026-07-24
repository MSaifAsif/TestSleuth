package dev.testsleuth.junit5;

import dev.testsleuth.core.event.EventId;
import dev.testsleuth.core.event.EventJsonReader;
import dev.testsleuth.core.event.EventJsonWriter;
import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.Subject;
import dev.testsleuth.core.event.SubjectType;
import dev.testsleuth.core.event.TestSleuthRunContext;
import dev.testsleuth.core.event.TestSleuthEvent;
import dev.testsleuth.core.event.TestSubjectIdentity;
import dev.testsleuth.core.jfr.TestSleuthJfrLifecycle;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class JUnitLifecycleEventCollector {
    private final List<TestSleuthEvent> events = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Long> startedNanos = new ConcurrentHashMap<>();
    private final Map<String, Long> phaseStartedNanos = new ConcurrentHashMap<>();
    private final Map<String, TestSleuthJfrLifecycle.Span> jfrTestSpans = new ConcurrentHashMap<>();
    private final Map<String, TestSleuthJfrLifecycle.Span> jfrPhaseSpans = new ConcurrentHashMap<>();
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
        jfrTestSpans.put(testIdentifier.getUniqueId(), TestSleuthJfrLifecycle.beginTest(
                subjectIdentifier(testIdentifier), testIdentifier.getDisplayName(), "junit5"
        ));
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
        TestSleuthJfrLifecycle.Span span = jfrTestSpans.remove(testIdentifier.getUniqueId());
        if (span != null) {
            span.finish(attributes.get("status"));
        }

        events.add(toEvent(EventKind.TEST_FINISHED, testIdentifier, attributes));
    }

    public List<TestSleuthEvent> events() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    void recordPhaseStarted(
            EventKind kind,
            String phase,
            String uniqueId,
            String displayName,
            Optional<Class<?>> testClass,
            Optional<Method> testMethod
    ) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(uniqueId, "uniqueId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(testClass, "testClass");
        Objects.requireNonNull(testMethod, "testMethod");

        phaseStartedNanos.put(phaseKey(phase, uniqueId), System.nanoTime());
        jfrPhaseSpans.put(phaseKey(phase, uniqueId), TestSleuthJfrLifecycle.beginPhase(
                phase,
                subjectIdentifier(displayName, testClass, testMethod),
                displayName,
                "junit5"
        ));
        events.add(toPhaseEvent(kind, phase, uniqueId, displayName, testClass, testMethod, Map.of()));
    }

    void recordPhaseFinished(
            EventKind kind,
            String phase,
            String uniqueId,
            String displayName,
            Optional<Class<?>> testClass,
            Optional<Method> testMethod
    ) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(uniqueId, "uniqueId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(testClass, "testClass");
        Objects.requireNonNull(testMethod, "testMethod");

        long now = System.nanoTime();
        String key = phaseKey(phase, uniqueId);
        long started = phaseStartedNanos.getOrDefault(key, now);
        long durationMillis = Math.max(0, Math.round((now - started) / 1_000_000.0));
        TestSleuthJfrLifecycle.Span span = jfrPhaseSpans.remove(key);
        if (span != null) {
            span.finish("completed");
        }
        events.add(toPhaseEvent(
                kind,
                phase,
                uniqueId,
                displayName,
                testClass,
                testMethod,
                Map.of("durationMillis", Long.toString(durationMillis))
        ));
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
            failureReporter.accept("Failed to write JUnit lifecycle events to " + eventsFile + ": " + e.getMessage());
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
        merged.addAll(events());
        return List.copyOf(merged);
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

    private TestSleuthEvent toPhaseEvent(
            EventKind kind,
            String phase,
            String uniqueId,
            String displayName,
            Optional<Class<?>> testClass,
            Optional<Method> testMethod,
            Map<String, String> attributes
    ) {
        Map<String, String> eventAttributes = new HashMap<>();
        eventAttributes.putAll(runContext.attributes());
        eventAttributes.put("collector", "junit5-listener");
        eventAttributes.put("phase", phase);
        eventAttributes.put("uniqueId", uniqueId);
        eventAttributes.put("displayName", displayName);
        testClass.map(Class::getName).ifPresent(value -> eventAttributes.put("className", value));
        testMethod.ifPresent(method -> {
            eventAttributes.put("methodName", method.getName());
            testClass.map(Class::getName)
                    .map(className -> TestSubjectIdentity.testMethod(className, method.getName()))
                    .ifPresent(value -> eventAttributes.put("testIdentity", value));
        });
        eventAttributes.putAll(attributes);

        return new TestSleuthEvent(
                new EventId("junit5:" + kind.name() + ":" + safeId(phase) + ":" + safeId(uniqueId)),
                Optional.empty(),
                kind,
                new Subject(subjectType(testClass, testMethod), subjectIdentifier(displayName, testClass, testMethod)),
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

    private static SubjectType subjectType(Optional<Class<?>> testClass, Optional<Method> testMethod) {
        if (testMethod.isPresent()) {
            return SubjectType.TEST_METHOD;
        }
        if (testClass.isPresent()) {
            return SubjectType.TEST_CLASS;
        }
        return SubjectType.SUITE;
    }

    private static String subjectIdentifier(
            String displayName,
            Optional<Class<?>> testClass,
            Optional<Method> testMethod
    ) {
        if (testClass.isPresent() && testMethod.isPresent()) {
            return TestSubjectIdentity.testMethod(testClass.orElseThrow().getName(), testMethod.orElseThrow().getName());
        }
        return testClass.map(Class::getName).orElse(displayName);
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

    private static String phaseKey(String phase, String uniqueId) {
        return phase + ":" + uniqueId;
    }
}

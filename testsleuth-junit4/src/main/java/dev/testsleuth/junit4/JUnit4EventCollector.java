package dev.testsleuth.junit4;

import dev.testsleuth.core.event.EventId;
import dev.testsleuth.core.event.EventJsonReader;
import dev.testsleuth.core.event.EventJsonWriter;
import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.Subject;
import dev.testsleuth.core.event.SubjectType;
import dev.testsleuth.core.event.TestSleuthEvent;
import dev.testsleuth.core.event.TestSleuthRunContext;
import dev.testsleuth.core.event.TestSubjectIdentity;
import dev.testsleuth.core.jfr.TestSleuthJfrLifecycle;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;

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

final class JUnit4EventCollector {
    private final List<TestSleuthEvent> events = new ArrayList<>();
    private final Map<String, Long> startedNanos = new HashMap<>();
    private final Map<String, String> statuses = new HashMap<>();
    private final Map<String, String> throwableTypes = new HashMap<>();
    private final Map<String, TestSleuthJfrLifecycle.Span> jfrTestSpans = new HashMap<>();
    private final TestSleuthRunContext runContext;

    JUnit4EventCollector() {
        this(TestSleuthRunContext.fromSystemProperties());
    }

    JUnit4EventCollector(TestSleuthRunContext runContext) {
        this.runContext = Objects.requireNonNull(runContext, "runContext");
    }

    void recordStarted(Description description) {
        Objects.requireNonNull(description, "description");
        if (!description.isTest()) {
            return;
        }

        startedNanos.put(uniqueId(description), System.nanoTime());
        jfrTestSpans.put(uniqueId(description), TestSleuthJfrLifecycle.beginTest(
                testIdentity(description), description.getDisplayName(), "junit4"
        ));
        events.add(toEvent(EventKind.TEST_STARTED, description, Map.of()));
    }

    void recordFailure(Failure failure) {
        Objects.requireNonNull(failure, "failure");
        Description description = failure.getDescription();
        if (description == null || !description.isTest()) {
            return;
        }
        statuses.put(uniqueId(description), "failed");
        throwableTypes.put(uniqueId(description), failure.getException().getClass().getName());
    }

    void recordAssumptionFailure(Failure failure) {
        Objects.requireNonNull(failure, "failure");
        Description description = failure.getDescription();
        if (description == null || !description.isTest()) {
            return;
        }
        statuses.put(uniqueId(description), "skipped");
        throwableTypes.put(uniqueId(description), failure.getException().getClass().getName());
    }

    void recordIgnored(Description description) {
        Objects.requireNonNull(description, "description");
        if (!description.isTest()) {
            return;
        }

        Map<String, String> attributes = new HashMap<>();
        attributes.put("status", "skipped");
        attributes.put("durationMillis", "0");
        events.add(toEvent(EventKind.TEST_FINISHED, description, attributes));
    }

    void recordFinished(Description description) {
        Objects.requireNonNull(description, "description");
        if (!description.isTest()) {
            return;
        }

        long now = System.nanoTime();
        long started = startedNanos.getOrDefault(uniqueId(description), now);
        long durationMillis = Math.max(0, Math.round((now - started) / 1_000_000.0));

        Map<String, String> attributes = new HashMap<>();
        attributes.put("status", statuses.getOrDefault(uniqueId(description), "passed"));
        attributes.put("durationMillis", Long.toString(durationMillis));
        Optional.ofNullable(throwableTypes.get(uniqueId(description)))
                .ifPresent(value -> attributes.put("throwableType", value));
        TestSleuthJfrLifecycle.Span span = jfrTestSpans.remove(uniqueId(description));
        if (span != null) {
            span.finish(attributes.get("status"));
        }

        events.add(toEvent(EventKind.TEST_FINISHED, description, attributes));
    }

    List<TestSleuthEvent> events() {
        return List.copyOf(events);
    }

    void writeTo(Path eventsFile, Consumer<String> failureReporter) {
        Objects.requireNonNull(eventsFile, "eventsFile");
        Objects.requireNonNull(failureReporter, "failureReporter");

        try {
            Path parent = eventsFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(eventsFile, new EventJsonWriter().write(mergedEvents(eventsFile)), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            failureReporter.accept("Failed to write JUnit 4 lifecycle events to " + eventsFile + ": " + e.getMessage());
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

    private TestSleuthEvent toEvent(EventKind kind, Description description, Map<String, String> attributes) {
        Map<String, String> eventAttributes = new HashMap<>();
        eventAttributes.putAll(runContext.attributes());
        eventAttributes.put("collector", "junit4-listener");
        eventAttributes.put("uniqueId", uniqueId(description));
        eventAttributes.put("displayName", description.getDisplayName());
        eventAttributes.put("className", className(description));
        eventAttributes.put("methodName", methodName(description));
        eventAttributes.put("testIdentity", testIdentity(description));
        eventAttributes.putAll(attributes);

        return new TestSleuthEvent(
                new EventId("junit4:" + kind.name() + ":" + safeId(uniqueId(description))),
                Optional.empty(),
                kind,
                new Subject(SubjectType.TEST_METHOD, testIdentity(description)),
                Instant.now(),
                System.nanoTime(),
                eventAttributes
        );
    }

    private static String uniqueId(Description description) {
        return className(description) + "#" + methodName(description);
    }

    private static String testIdentity(Description description) {
        return TestSubjectIdentity.testMethod(className(description), methodName(description));
    }

    private static String className(Description description) {
        Class<?> testClass = description.getTestClass();
        if (testClass != null) {
            return testClass.getName();
        }
        String className = description.getClassName();
        return className == null || className.isBlank() ? "unknown" : className;
    }

    private static String methodName(Description description) {
        String methodName = description.getMethodName();
        return methodName == null || methodName.isBlank() ? description.getDisplayName() : methodName;
    }

    private static String safeId(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}

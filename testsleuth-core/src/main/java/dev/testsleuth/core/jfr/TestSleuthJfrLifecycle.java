package dev.testsleuth.core.jfr;

import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

import java.util.Objects;

public final class TestSleuthJfrLifecycle {
    private TestSleuthJfrLifecycle() {
    }

    public static Span beginTest(String testIdentity, String displayName, String framework) {
        return beginPhase("test", testIdentity, displayName, framework);
    }

    public static Span beginPhase(String phase, String testIdentity, String displayName, String framework) {
        TestLifecycleEvent event = new TestLifecycleEvent();
        event.phase = requireText(phase, "phase");
        event.testIdentity = requireText(testIdentity, "testIdentity");
        event.displayName = requireText(displayName, "displayName");
        event.framework = requireText(framework, "framework");
        event.begin();
        return new Span(event);
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    public static final class Span {
        private final TestLifecycleEvent event;

        private Span(TestLifecycleEvent event) {
            this.event = event;
        }

        public void finish(String outcome) {
            event.outcome = requireText(outcome, "outcome");
            event.end();
            event.commit();
        }
    }

    @Name("dev.testsleuth.TestLifecycle")
    @Label("TestSleuth Test Lifecycle")
    public static final class TestLifecycleEvent extends Event {
        @Label("Phase")
        public String phase;

        @Label("Test Identity")
        public String testIdentity;

        @Label("Display Name")
        public String displayName;

        @Label("Framework")
        public String framework;

        @Label("Outcome")
        public String outcome;
    }
}

package dev.testsleuth.junit5;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.nio.file.Path;
import java.util.Optional;

public final class TestSleuthJUnit5Listener implements TestExecutionListener {
    public static final String EVENTS_FILE_PROPERTY = "testsleuth.junit.events.file";

    private final JUnitLifecycleEventCollector collector = new JUnitLifecycleEventCollector();
    private final Optional<Path> eventsFile;

    public TestSleuthJUnit5Listener() {
        this.eventsFile = configuredEventsFile();
    }

    TestSleuthJUnit5Listener(Path eventsFile) {
        this.eventsFile = Optional.of(eventsFile);
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        collector.recordDiscoveredTests(testPlan);
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        collector.recordStarted(testIdentifier);
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        collector.recordFinished(testIdentifier, testExecutionResult);
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        eventsFile.ifPresent(path -> collector.writeTo(path, message -> System.err.println("[TestSleuth] " + message)));
    }

    private static Optional<Path> configuredEventsFile() {
        String configured = System.getProperty(EVENTS_FILE_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Path.of(configured));
    }
}


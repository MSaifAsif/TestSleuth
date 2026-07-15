package dev.testsleuth.junit4;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.nio.file.Path;
import java.util.Optional;

public final class TestSleuthJUnit4RunListener extends RunListener {
    public static final String EVENTS_FILE_PROPERTY = "testsleuth.junit4.events.file";

    private final JUnit4EventCollector collector = new JUnit4EventCollector();
    private final Optional<Path> eventsFile;

    public TestSleuthJUnit4RunListener() {
        this.eventsFile = configuredEventsFile();
    }

    TestSleuthJUnit4RunListener(Path eventsFile) {
        this.eventsFile = Optional.of(eventsFile);
    }

    @Override
    public void testStarted(Description description) {
        collector.recordStarted(description);
    }

    @Override
    public void testFailure(Failure failure) {
        collector.recordFailure(failure);
    }

    @Override
    public void testFinished(Description description) {
        collector.recordFinished(description);
    }

    @Override
    public void testRunFinished(Result result) {
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

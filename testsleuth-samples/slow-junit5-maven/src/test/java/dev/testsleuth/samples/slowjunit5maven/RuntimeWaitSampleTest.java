package dev.testsleuth.samples.slowjunit5maven;

import dev.testsleuth.runtimewait.RuntimeWaitCollector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;

final class RuntimeWaitSampleTest {
    private static final RuntimeWaitCollector COLLECTOR = new RuntimeWaitCollector();

    @Test
    void runtimeWaitCollectorRecordsDirectWaits() {
        COLLECTOR.parkNanos(Duration.ZERO);

        assertFalse(COLLECTOR.events().isEmpty());
    }

    @AfterAll
    static void writeRuntimeWaitEvents() {
        String eventsFile = System.getProperty(RuntimeWaitCollector.EVENTS_FILE_PROPERTY);
        if (eventsFile == null || eventsFile.isBlank()) {
            return;
        }
        COLLECTOR.writeTo(Path.of(eventsFile), failure -> {
            throw new AssertionError(failure);
        });
    }
}

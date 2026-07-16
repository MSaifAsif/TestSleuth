package dev.testsleuth.samples.slowjunit5maven;

import dev.testsleuth.runtimewait.RuntimeWaitCollector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "testsleuth.sample.worstCase", matches = "true")
final class WorstCaseTestSleuthSampleTest {
    private static final RuntimeWaitCollector COLLECTOR = new RuntimeWaitCollector();
    private static final SimulatedApplicationContextCache APPLICATION_CONTEXTS =
            new SimulatedApplicationContextCache();

    @Test
    void fullApplicationContextWithFixedPollingAndRuntimeWaits() throws InterruptedException {
        SimulatedApplicationContext context = APPLICATION_CONTEXTS.load("full-stack-worst-case");

        Thread.sleep(325);
        COLLECTOR.sleep(Duration.ofMillis(275));

        for (int attempt = 0; attempt < 3 && !context.hasBean("invoiceService"); attempt++) {
            Thread.sleep(125);
        }

        assertTrue(context.hasBean("invoiceService"));
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

    private static final class SimulatedApplicationContextCache {
        private final ConcurrentMap<String, SimulatedApplicationContext> contexts = new ConcurrentHashMap<>();

        private SimulatedApplicationContext load(String fingerprint) throws InterruptedException {
            SimulatedApplicationContext existing = contexts.get(fingerprint);
            if (existing != null) {
                return existing;
            }

            Thread.sleep(650);
            SimulatedApplicationContext context = new SimulatedApplicationContext(fingerprint, true);
            contexts.put(fingerprint, context);
            return context;
        }
    }

    private record SimulatedApplicationContext(String fingerprint, boolean invoiceServiceAvailable) {
        private boolean hasBean(String beanName) {
            return !fingerprint.isBlank() && invoiceServiceAvailable && "invoiceService".equals(beanName);
        }
    }
}

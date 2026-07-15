package dev.testsleuth.samples.slowjunit5maven;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class FrameworkInitializationSampleTest {
    private static final SimulatedApplicationContextCache CONTEXTS = new SimulatedApplicationContextCache();

    @Test
    void fullApplicationContextForNarrowAssertion() throws InterruptedException {
        SimulatedApplicationContext context = CONTEXTS.load("full-web-stack");

        assertTrue(context.hasBean("invoiceService"));
    }

    @Test
    void slightlyDifferentProfileCreatesAnotherContext() throws InterruptedException {
        SimulatedApplicationContext context = CONTEXTS.load("full-web-stack-with-test-profile");

        assertTrue(context.hasBean("invoiceService"));
    }

    private static final class SimulatedApplicationContextCache {
        private final Map<String, SimulatedApplicationContext> contexts = new ConcurrentHashMap<>();

        private SimulatedApplicationContext load(String fingerprint) throws InterruptedException {
            SimulatedApplicationContext existing = contexts.get(fingerprint);
            if (existing != null) {
                return existing;
            }

            Thread.sleep(650);
            SimulatedApplicationContext context = new SimulatedApplicationContext(fingerprint);
            contexts.put(fingerprint, context);
            return context;
        }
    }

    private record SimulatedApplicationContext(String fingerprint) {
        private boolean hasBean(String beanName) {
            return !fingerprint.isBlank() && "invoiceService".equals(beanName);
        }
    }
}

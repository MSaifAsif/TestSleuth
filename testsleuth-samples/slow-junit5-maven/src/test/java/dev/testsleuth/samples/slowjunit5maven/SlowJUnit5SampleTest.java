package dev.testsleuth.samples.slowjunit5maven;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SlowJUnit5SampleTest {
    @BeforeEach
    void repeatedSetup() throws InterruptedException {
        Thread.sleep(150);
    }

    @Test
    void fastUnitStyleTest() {
        assertEquals(4, 2 + 2);
    }

    @Test
    void setupHeavyButBelowDefaultThreshold() {
        assertTrue("testsleuth".startsWith("test"));
    }

    @Test
    void slowExternalCallSimulation() throws InterruptedException {
        Thread.sleep(1_250);

        assertTrue(true);
    }
}

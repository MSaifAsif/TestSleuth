package dev.testsleuth.samples.slowjunit5maven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class SlowFailsafeSampleIT {
    @Test
    void slowIntegrationBoundarySimulation() throws InterruptedException {
        Thread.sleep(1_050);

        assertTrue(true);
    }
}

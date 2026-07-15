package dev.testsleuth.samples.slowjunit5maven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class FixedWaitSampleTest {
    @Test
    void fixedSleepWaitingForExternalSignal() throws InterruptedException {
        Thread.sleep(1_100);

        assertTrue(true);
    }

    @Test
    void repeatedShortPollingDelay() throws InterruptedException {
        for (int attempt = 0; attempt < 4; attempt++) {
            Thread.sleep(125);
        }

        assertTrue(true);
    }
}

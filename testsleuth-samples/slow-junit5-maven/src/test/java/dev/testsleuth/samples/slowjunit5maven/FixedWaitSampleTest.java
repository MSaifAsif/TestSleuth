package dev.testsleuth.samples.slowjunit5maven;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

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

    @Test
    void timedConcurrencyWaits() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(450, TimeUnit.MILLISECONDS);

        Semaphore semaphore = new Semaphore(0);
        while (!semaphore.tryAcquire(150, TimeUnit.MILLISECONDS)) {
            break;
        }

        assertTrue(true);
    }
}

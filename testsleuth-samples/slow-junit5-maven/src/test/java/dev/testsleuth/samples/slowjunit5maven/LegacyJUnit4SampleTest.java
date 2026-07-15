package dev.testsleuth.samples.slowjunit5maven;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class LegacyJUnit4SampleTest {
    @Test
    public void legacySlowExternalCallSimulation() throws InterruptedException {
        Thread.sleep(1_050);

        assertEquals("legacy-result", "legacy-" + "result");
    }
}

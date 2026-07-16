package dev.testsleuth.samples.slowjunit4maven;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SlowJUnit4SampleTest {
    @Test
    public void legacySlowExternalCallSimulation() throws InterruptedException {
        Thread.sleep(1_075);

        assertEquals("legacy-result", "legacy-" + "result");
    }

    @Test
    public void legacyFastBaseline() {
        assertEquals(4, 2 + 2);
    }
}

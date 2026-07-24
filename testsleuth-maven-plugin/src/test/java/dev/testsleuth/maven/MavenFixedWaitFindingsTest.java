package dev.testsleuth.maven;

import dev.testsleuth.core.event.TestSleuthRunContext;
import dev.testsleuth.core.finding.Finding;
import dev.testsleuth.core.finding.FindingCategory;
import dev.testsleuth.core.finding.FindingSeverity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MavenFixedWaitFindingsTest {
    @TempDir
    private Path tempDir;

    @Test
    void returnsNoFindingsWhenDetectorIsDisabled() throws IOException {
        writeSampleSource();
        TestSleuthMavenConfig config = TestSleuthMavenConfig.from(true, "summary", 1_000, 5_000, 10, true, false, 250);

        List<Finding> findings = new MavenFixedWaitFindings(config, runContext())
                .detect(List.of(tempDir.toString()));

        assertEquals(0, findings.size());
    }

    @Test
    void detectsThreadSleepCallsAboveThreshold() throws IOException {
        writeSampleSource();
        TestSleuthMavenConfig config = TestSleuthMavenConfig.from(true, "summary", 1_000, 5_000, 10, true, true, 250);

        List<Finding> findings = new MavenFixedWaitFindings(config, runContext())
                .detect(List.of(tempDir.toString()));

        assertEquals(4, findings.size());
        Finding largest = findings.get(0);
        assertEquals("Fixed wait in test source: ExampleTest.java:12", largest.title());
        assertEquals(FindingCategory.WAITING, largest.category());
        assertEquals(FindingSeverity.HIGH, largest.severity());
        assertEquals(dev.testsleuth.core.finding.EvidenceType.POTENTIAL, largest.evidenceType());
        assertEquals(dev.testsleuth.core.finding.AttributionScope.UNCLASSIFIED, largest.attributionScope());
        assertEquals(0, largest.observedCost().toMillis());
        assertEquals("await(timeout) waited up to 2000 ms at ExampleTest.java:12.", largest.evidence().get(0));
        assertEquals("Detector: fixed-waits-source.", largest.evidence().get(1));
        assertEquals("Module: dev.testsleuth:sample.", largest.evidence().get(2));
    }

    private void writeSampleSource() throws IOException {
        Files.writeString(tempDir.resolve("ExampleTest.java"), """
                final class ExampleTest {
                    void ignoredShortWait() throws Exception {
                        Thread.sleep(125);
                    }
                    void fixedWait() throws Exception {
                        Thread.sleep(1_100);
                    }
                    void mediumWait() throws Exception {
                        Thread.sleep(325L);
                    }
                    void latchWait(java.util.concurrent.CountDownLatch latch) throws Exception {
                        latch.await(2, java.util.concurrent.TimeUnit.SECONDS);
                    }
                    void futureWait(java.util.concurrent.Future<String> future) throws Exception {
                        future.get(750, java.util.concurrent.TimeUnit.MILLISECONDS);
                    }
                    void commentOnly() {
                        // Thread.sleep(9000);
                    }
                }
                """);
    }

    private static TestSleuthRunContext runContext() {
        return new TestSleuthRunContext(
                "run-1",
                "dev.testsleuth:sample",
                "dev.testsleuth",
                "sample",
                "0.1.0-SNAPSHOT",
                "/workspace/sample",
                "456",
                "unknown"
        );
    }
}

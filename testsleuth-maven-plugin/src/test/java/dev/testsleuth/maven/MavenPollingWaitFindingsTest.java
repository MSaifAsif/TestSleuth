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

final class MavenPollingWaitFindingsTest {
    @TempDir
    private Path tempDir;

    @Test
    void returnsNoFindingsWhenDetectorIsDisabled() throws IOException {
        writeSampleSource();
        TestSleuthMavenConfig config = TestSleuthMavenConfig.from(
                true, "summary", 1_000, 5_000, 10, true, false, 250, false, 100
        );

        List<Finding> findings = new MavenPollingWaitFindings(config, runContext())
                .detect(List.of(tempDir.toString()));

        assertEquals(0, findings.size());
    }

    @Test
    void detectsThreadSleepInsideLoopAboveThreshold() throws IOException {
        writeSampleSource();
        TestSleuthMavenConfig config = TestSleuthMavenConfig.from(
                true, "summary", 1_000, 5_000, 10, true, false, 250, true, 100
        );

        List<Finding> findings = new MavenPollingWaitFindings(config, runContext())
                .detect(List.of(tempDir.toString()));

        assertEquals(2, findings.size());
        Finding finding = findings.get(0);
        assertEquals("Polling wait in test source: ExampleTest.java:11", finding.title());
        assertEquals(FindingCategory.WAITING, finding.category());
        assertEquals(FindingSeverity.MEDIUM, finding.severity());
        assertEquals(250, finding.observedCost().toMillis());
        assertEquals("tryAcquire(timeout) waited up to 250 ms inside a nearby loop at ExampleTest.java:11.",
                finding.evidence().get(0));
        assertEquals("Detector: polling-waits-source.", finding.evidence().get(1));
    }

    private void writeSampleSource() throws IOException {
        Files.writeString(tempDir.resolve("ExampleTest.java"), """
                final class ExampleTest {
                    void polls() throws Exception {
                        for (int attempt = 0; attempt < 4; attempt++) {
                            Thread.sleep(125);
                        }
                    }
                    void nonPollingWait() throws Exception {
                        Thread.sleep(325);
                    }
                    void pollsSemaphore(java.util.concurrent.Semaphore semaphore) throws Exception {
                        while (!semaphore.tryAcquire(250, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                            // keep polling
                        }
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

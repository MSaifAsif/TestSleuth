package dev.testsleuth.maven;

import dev.testsleuth.core.jfr.TestSleuthJfrLifecycle;
import jdk.jfr.Recording;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MavenJfrTestAttributionTest {
    @TempDir
    Path tempDir;

    @Test
    void attributesSameThreadSleepInsideATestLifecycleWindow() throws Exception {
        Path recordingFile = tempDir.resolve("attribution.jfr");
        try (Recording recording = new Recording()) {
            recording.enable(TestSleuthJfrLifecycle.TestLifecycleEvent.class);
            recording.enable("jdk.ThreadSleep").withThreshold(Duration.ZERO).withStackTrace();
            recording.start();
            TestSleuthJfrLifecycle.Span span = TestSleuthJfrLifecycle.beginTest(
                    "example.SampleTest.slowTest", "slowTest()", "junit5"
            );
            Thread.sleep(20);
            span.finish("passed");
            recording.stop();
            recording.dump(recordingFile);
        }

        MavenJfrTestAttribution.Summary summary = new MavenJfrTestAttribution().summarize(tempDir, true);

        assertEquals(1, summary.testWindowCount());
        assertEquals(1, summary.evidence().size());
        assertEquals("example.SampleTest.slowTest", summary.evidence().get(0).testIdentity());
        assertEquals(1L, summary.evidence().get(0).eventCounts().get("ThreadSleep"));
        assertTrue(summary.evidence().get(0).eventLocations().get("ThreadSleep").contains("MavenJfrTestAttributionTest"));
    }

    @Test
    void attributesNestedSetupSleepToTheSetupPhaseInsteadOfTheTestBody() throws Exception {
        Path recordingFile = tempDir.resolve("phase-attribution.jfr");
        try (Recording recording = new Recording()) {
            recording.enable(TestSleuthJfrLifecycle.TestLifecycleEvent.class);
            recording.enable("jdk.ThreadSleep").withThreshold(Duration.ZERO);
            recording.start();
            TestSleuthJfrLifecycle.Span testSpan = TestSleuthJfrLifecycle.beginTest(
                    "example.SampleTest.slowTest", "slowTest()", "junit5"
            );
            TestSleuthJfrLifecycle.Span setupSpan = TestSleuthJfrLifecycle.beginPhase(
                    "before-each", "example.SampleTest.slowTest", "slowTest()", "junit5"
            );
            Thread.sleep(20);
            setupSpan.finish("completed");
            Thread.sleep(20);
            testSpan.finish("passed");
            recording.stop();
            recording.dump(recordingFile);
        }

        MavenJfrTestAttribution.Summary summary = new MavenJfrTestAttribution().summarize(tempDir, true);

        assertEquals(1, summary.evidence().size());
        assertEquals(1L, summary.evidence().get(0).eventCounts().get("ThreadSleep"));
        assertEquals(1, summary.phaseEvidence().size());
        MavenJfrTestAttribution.PhaseEvidence setupEvidence = summary.phaseEvidence().get(0);
        assertEquals("before-each", setupEvidence.phase());
        assertEquals(1L, setupEvidence.eventCounts().get("ThreadSleep"));
        assertTrue(summary.consoleLines().stream().anyMatch(line -> line.contains("JFR phase evidence")));
    }

    @Test
    void doesNotMatchAnEventFromAnotherRecordingWithAnOverlappingThreadAndTimestamp() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = start.plusSeconds(1);

        assertTrue(MavenJfrTestAttribution.belongsToWindow(
                tempDir.resolve("fork-one.jfr"), 42L, start, end,
                tempDir.resolve("fork-one.jfr"), 42L, start.plusMillis(100)
        ));
        assertFalse(MavenJfrTestAttribution.belongsToWindow(
                tempDir.resolve("fork-one.jfr"), 42L, start, end,
                tempDir.resolve("fork-two.jfr"), 42L, start.plusMillis(100)
        ));
    }

    @Test
    void classifiesUnownedRuntimeEvidenceByConcurrentTestOwnership() {
        assertEquals(
                MavenJfrTestAttribution.UnassignedScope.UNCLASSIFIED,
                MavenJfrTestAttribution.unassignedScopeForActiveTests(0)
        );
        assertEquals(
                MavenJfrTestAttribution.UnassignedScope.CORRELATED_ASYNCHRONOUS,
                MavenJfrTestAttribution.unassignedScopeForActiveTests(1)
        );
        assertEquals(
                MavenJfrTestAttribution.UnassignedScope.SHARED_CONCURRENT,
                MavenJfrTestAttribution.unassignedScopeForActiveTests(2)
        );
    }

    @Test
    void classifiesGarbageCollectionAsSharedJvmWorkWhenTestsAreActive() {
        assertEquals(
                MavenJfrTestAttribution.UnassignedScope.UNCLASSIFIED,
                MavenJfrTestAttribution.sharedJvmScopeForActiveTests(0)
        );
        assertEquals(
                MavenJfrTestAttribution.UnassignedScope.SHARED_JVM,
                MavenJfrTestAttribution.sharedJvmScopeForActiveTests(1)
        );
    }

    @Test
    void reportsGarbageCollectionPauseTimeSeparatelyFromOtherUnownedWork() {
        MavenJfrTestAttribution.UnassignedEvidence evidence = new MavenJfrTestAttribution.UnassignedEvidence(
                MavenJfrTestAttribution.UnassignedScope.UNCLASSIFIED,
                java.util.Map.of("GarbageCollection", 1L, "ThreadPark", 1L),
                java.util.Map.of("GarbageCollection", Duration.ofMillis(4), "ThreadPark", Duration.ofMillis(900)),
                Duration.ofMillis(904)
        );
        MavenJfrTestAttribution.Summary summary = new MavenJfrTestAttribution.Summary(
                0, 0, 0, java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(evidence)
        );

        assertTrue(summary.consoleLines().stream()
                .anyMatch(line -> line.contains("GarbageCollection pause time 4 ms")));
    }
}

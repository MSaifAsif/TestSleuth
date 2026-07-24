package dev.testsleuth.maven;

import dev.testsleuth.core.finding.AttributionScope;
import dev.testsleuth.core.finding.EvidenceType;
import dev.testsleuth.core.finding.Finding;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MavenJfrRuntimeFindingsTest {
    private final TestSleuthMavenConfig config = TestSleuthMavenConfig.from(
            true, "summary", 500, 1_000, 10, true, false, 250,
            false, 100, false, false, false
    );

    @Test
    void createsMeasuredFindingForDirectRuntimeCause() {
        MavenJfrTestAttribution.TestEvidence evidence = new MavenJfrTestAttribution.TestEvidence(
                "example.SampleTest.slowTest",
                Map.of("ThreadSleep", 1L),
                Map.of("ThreadSleep", Duration.ofMillis(900)),
                Map.of("ThreadSleep", "example.SampleTest.slowTest:42"),
                Duration.ofMillis(900)
        );

        List<Finding> findings = new MavenJfrRuntimeFindings(config).detect(summary(List.of(evidence), List.of(), List.of()));

        Finding finding = findings.get(0);
        assertEquals(EvidenceType.MEASURED, finding.evidenceType());
        assertEquals(AttributionScope.DIRECT_TEST_THREAD, finding.attributionScope());
        assertEquals(900, finding.observedCost().toMillis());
        assertTrue(finding.evidence().contains("User-code stack: example.SampleTest.slowTest:42."));
    }

    @Test
    void createsNonDurationFindingForSampledCpuActivity() {
        MavenJfrTestAttribution.SampleEvidence evidence = new MavenJfrTestAttribution.SampleEvidence(
                "example.SampleTest.cpuTest", Map.of("ExecutionSample", 2L), Map.of()
        );

        List<Finding> findings = new MavenJfrRuntimeFindings(config).detect(new MavenJfrTestAttribution.Summary(
                1, 0, 2, List.of(), List.of(), List.of(evidence), List.of(), List.of()
        ));

        Finding finding = findings.get(0);
        assertEquals(EvidenceType.CORRELATED, finding.evidenceType());
        assertEquals(Duration.ZERO, finding.observedCost());
        assertTrue(finding.evidence().contains("Sample counts indicate activity, not elapsed time."));
    }

    @Test
    void createsSharedJvmFindingForGarbageCollectionPause() {
        MavenJfrTestAttribution.UnassignedEvidence evidence = new MavenJfrTestAttribution.UnassignedEvidence(
                MavenJfrTestAttribution.UnassignedScope.SHARED_JVM,
                Map.of("GarbageCollection", 2L),
                Map.of("GarbageCollection", Duration.ofMillis(40)),
                Duration.ofMillis(40)
        );

        List<Finding> findings = new MavenJfrRuntimeFindings(config).detect(summary(List.of(), List.of(), List.of(evidence)));

        Finding finding = findings.get(0);
        assertEquals(AttributionScope.SHARED_JVM, finding.attributionScope());
        assertEquals(EvidenceType.CORRELATED, finding.evidenceType());
        assertEquals(40, finding.observedCost().toMillis());
    }

    private static MavenJfrTestAttribution.Summary summary(
            List<MavenJfrTestAttribution.TestEvidence> evidence,
            List<MavenJfrTestAttribution.SampleEvidence> samples,
            List<MavenJfrTestAttribution.UnassignedEvidence> unassigned
    ) {
        return new MavenJfrTestAttribution.Summary(1, 1, 0, evidence, List.of(), samples, List.of(), unassigned);
    }
}

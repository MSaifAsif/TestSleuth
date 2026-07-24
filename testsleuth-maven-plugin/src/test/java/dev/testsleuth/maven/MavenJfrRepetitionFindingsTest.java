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

final class MavenJfrRepetitionFindingsTest {
    private final TestSleuthMavenConfig config = TestSleuthMavenConfig.from(
            true, "summary", 1_000, 5_000, 10, true, false, 250, false, 100, false
    );

    @Test
    void groupsTheSameDirectRuntimeOperationAcrossTests() {
        List<Finding> findings = new MavenJfrRepetitionFindings(config).detect(summary(
                evidence("example.FirstTest.first", 600, "example.ContextCache.load:24"),
                evidence("example.SecondTest.second", 700, "example.ContextCache.load:24")
        ));

        assertEquals(1, findings.size());
        Finding finding = findings.get(0);
        assertEquals("Repeated JFR fixed wait: example.ContextCache.load:24", finding.title());
        assertEquals(EvidenceType.MEASURED, finding.evidenceType());
        assertEquals(AttributionScope.DIRECT_TEST_THREAD, finding.attributionScope());
        assertEquals(1_300, finding.observedCost().toMillis());
        assertEquals(2, finding.affectedSubjects().size());
        assertTrue(finding.evidence().contains("Longest single observation: 700 ms."));
    }

    @Test
    void doesNotGroupDifferentStacksOrSingleTestEvidence() {
        List<Finding> findings = new MavenJfrRepetitionFindings(config).detect(summary(
                evidence("example.FirstTest.first", 600, "example.ContextCache.load:24"),
                evidence("example.SecondTest.second", 700, "example.OtherCache.load:24")
        ));

        assertTrue(findings.isEmpty());
    }

    private static MavenJfrTestAttribution.Summary summary(MavenJfrTestAttribution.TestEvidence... evidence) {
        return new MavenJfrTestAttribution.Summary(
                evidence.length,
                evidence.length,
                0,
                List.of(evidence),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static MavenJfrTestAttribution.TestEvidence evidence(String testIdentity, long duration, String stack) {
        return new MavenJfrTestAttribution.TestEvidence(
                testIdentity,
                Map.of("ThreadSleep", 1L),
                Map.of("ThreadSleep", Duration.ofMillis(duration)),
                Map.of("ThreadSleep", stack),
                Duration.ofMillis(duration)
        );
    }
}

package dev.testsleuth.maven;

import dev.testsleuth.core.event.TestSleuthRunContext;
import dev.testsleuth.core.finding.AttributionScope;
import dev.testsleuth.core.finding.EvidenceType;
import dev.testsleuth.core.finding.Finding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MavenJfrFrameworkInitializationFindingsTest {
    @TempDir
    private Path tempDir;

    @Test
    void createsAnInferredFindingWhenDirectJfrEvidenceMatchesFrameworkSource() throws IOException {
        writeFrameworkSource();
        MavenJfrFrameworkInitializationFindings.Result result = detector().detect(
                List.of(tempDir.toString()),
                summary("dev.testsleuth.ExampleContextTest$ContextCache.load:24")
        );

        assertEquals(1, result.findings().size());
        Finding finding = result.findings().get(0);
        assertEquals("Runtime-backed framework initialization: dev.testsleuth.ExampleContextTest", finding.title());
        assertEquals(EvidenceType.INFERRED, finding.evidenceType());
        assertEquals(AttributionScope.FRAMEWORK_OR_FIXTURE, finding.attributionScope());
        assertEquals(1_400, finding.observedCost().toMillis());
        assertTrue(finding.evidence().contains("Observed runtime causes: {ThreadSleep=1400}."));
        assertEquals(java.util.Set.of("dev.testsleuth.ExampleContextTest"), result.runtimeBackedClassNames());
    }

    @Test
    void doesNotInferFrameworkInitializationFromAnUnrelatedRuntimeStack() throws IOException {
        writeFrameworkSource();
        MavenJfrFrameworkInitializationFindings.Result result = detector().detect(
                List.of(tempDir.toString()),
                summary("dev.testsleuth.OtherTest.waitForService:24")
        );

        assertTrue(result.findings().isEmpty());
        assertTrue(result.runtimeBackedClassNames().isEmpty());
    }

    private MavenJfrFrameworkInitializationFindings detector() {
        TestSleuthMavenConfig config = TestSleuthMavenConfig.from(
                true, "summary", 1_000, 5_000, 10, true, false, 250, false, 100, true
        );
        return new MavenJfrFrameworkInitializationFindings(config, new TestSleuthRunContext(
                "run-1", "dev.testsleuth:sample", "dev.testsleuth", "sample", "0.1.0-SNAPSHOT",
                "/workspace/sample", "456", "unknown"
        ));
    }

    private static MavenJfrTestAttribution.Summary summary(String stackLocation) {
        MavenJfrTestAttribution.TestEvidence evidence = new MavenJfrTestAttribution.TestEvidence(
                "dev.testsleuth.ExampleContextTest.loadsContext",
                Map.of("ThreadSleep", 2L),
                Map.of("ThreadSleep", Duration.ofMillis(1_400)),
                Map.of("ThreadSleep", stackLocation),
                Duration.ofMillis(1_400)
        );
        return new MavenJfrTestAttribution.Summary(1, 2, 0, List.of(evidence), List.of(), List.of(), List.of(), List.of());
    }

    private void writeFrameworkSource() throws IOException {
        Files.writeString(tempDir.resolve("ExampleContextTest.java"), """
                package dev.testsleuth;

                final class ExampleContextTest {
                    ApplicationContext context;
                }
                """);
    }
}

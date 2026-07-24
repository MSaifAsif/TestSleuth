package dev.testsleuth.maven;

import dev.testsleuth.core.event.EventId;
import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.Subject;
import dev.testsleuth.core.event.SubjectType;
import dev.testsleuth.core.event.TestSleuthEvent;
import dev.testsleuth.core.event.TestSleuthRunContext;
import dev.testsleuth.core.finding.Finding;
import dev.testsleuth.core.finding.FindingCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MavenFrameworkInitializationFindingsTest {
    @TempDir
    private Path tempDir;

    @Test
    void returnsNoFindingsWhenDetectorIsDisabled() throws IOException {
        writeSampleSource();
        TestSleuthMavenConfig config = TestSleuthMavenConfig.from(
                true, "summary", 1_000, 5_000, 10, true, false, 250, false, 100, false
        );

        List<Finding> findings = new MavenFrameworkInitializationFindings(config, runContext())
                .detect(List.of(tempDir.toString()), List.of(event(1_400)));

        assertEquals(0, findings.size());
    }

    @Test
    void detectsFrameworkIndicatorsWhenClassDurationIsAboveThreshold() throws IOException {
        writeSampleSource();
        TestSleuthMavenConfig config = TestSleuthMavenConfig.from(
                true, "summary", 1_000, 5_000, 10, true, false, 250, false, 100, true
        );

        List<Finding> findings = new MavenFrameworkInitializationFindings(config, runContext())
                .detect(List.of(tempDir.toString()), List.of(event(1_400)));

        assertEquals(1, findings.size());
        Finding finding = findings.get(0);
        assertEquals(
                "Framework initialization candidate: dev.testsleuth.ExampleContextTest",
                finding.title()
        );
        assertEquals(FindingCategory.SPRING_CONTEXT, finding.category());
        assertEquals(dev.testsleuth.core.finding.EvidenceType.POTENTIAL, finding.evidenceType());
        assertEquals(dev.testsleuth.core.finding.AttributionScope.UNCLASSIFIED, finding.attributionScope());
        assertEquals(0, finding.observedCost().toMillis());
        assertEquals(
                "Observed class duration 1400 ms for dev.testsleuth.ExampleContextTest.",
                finding.evidence().get(0)
        );
        assertEquals("Framework indicators: application context usage.", finding.evidence().get(1));
    }

    @Test
    void ignoresFrameworkIndicatorsBelowThreshold() throws IOException {
        writeSampleSource();
        TestSleuthMavenConfig config = TestSleuthMavenConfig.from(
                true, "summary", 1_000, 5_000, 10, true, false, 250, false, 100, true
        );

        List<Finding> findings = new MavenFrameworkInitializationFindings(config, runContext())
                .detect(List.of(tempDir.toString()), List.of(event(900)));

        assertEquals(0, findings.size());
    }

    private void writeSampleSource() throws IOException {
        Files.writeString(tempDir.resolve("ExampleContextTest.java"), """
                package dev.testsleuth;

                final class ExampleContextTest {
                    void usesContext() {
                        ApplicationContext context = null;
                    }
                }
                """);
    }

    private static TestSleuthEvent event(long durationMillis) {
        return new TestSleuthEvent(
                new EventId("event-1"),
                Optional.empty(),
                EventKind.TEST_FINISHED,
                new Subject(SubjectType.TEST_METHOD, "dev.testsleuth.ExampleContextTest.usesContext"),
                Instant.EPOCH,
                0,
                Map.of(
                        "collector", "maven-test-report",
                        "className", "dev.testsleuth.ExampleContextTest",
                        "durationMillis", Long.toString(durationMillis)
                )
        );
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

package dev.testsleuth.maven;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class MavenJfrRuntimeCauseExplainerTest {
    @Test
    void explainsDirectFixedWaitWithAnAction() {
        MavenJfrTestAttribution.TestEvidence evidence = new MavenJfrTestAttribution.TestEvidence(
                "example.SampleTest.slowTest",
                Map.of("ThreadSleep", 1L),
                Map.of("ThreadSleep", Duration.ofMillis(900)),
                Map.of("ThreadSleep", "example.SampleTest.slowTest:42"),
                Duration.ofMillis(900)
        );
        MavenJfrTestAttribution.Summary summary = new MavenJfrTestAttribution.Summary(
                1, 1, 0, List.of(evidence), List.of(), List.of(), List.of(), List.of()
        );

        List<String> lines = new MavenJfrRuntimeCauseExplainer().consoleLines(summary);

        assertTrue(lines.get(0).contains("fixed wait"));
        assertTrue(lines.get(0).contains("condition-driven wait"));
        assertTrue(lines.get(0).contains("stack: example.SampleTest.slowTest:42"));
    }

    @Test
    void explainsSetupFileIoWithoutAttributingItToTheTestBody() {
        MavenJfrTestAttribution.PhaseEvidence evidence = new MavenJfrTestAttribution.PhaseEvidence(
                "example.SampleTest.slowTest",
                "before-each",
                Map.of("FileRead", 1L),
                Map.of("FileRead", Duration.ofMillis(400)),
                Map.of(),
                Duration.ofMillis(400)
        );
        MavenJfrTestAttribution.Summary summary = new MavenJfrTestAttribution.Summary(
                1, 1, 0, List.of(), List.of(evidence), List.of(), List.of(), List.of()
        );

        List<String> lines = new MavenJfrRuntimeCauseExplainer().consoleLines(summary);

        assertTrue(lines.get(0).contains("before-each file I/O"));
        assertTrue(lines.get(0).contains("in-memory test seam"));
    }

    @Test
    void explainsCpuSamplesWithoutPresentingThemAsElapsedTime() {
        MavenJfrTestAttribution.SampleEvidence evidence = new MavenJfrTestAttribution.SampleEvidence(
                "example.SampleTest.cpuHeavyTest",
                Map.of("ExecutionSample", 3L),
                Map.of("ExecutionSample", "example.FixtureFactory.create:18")
        );
        MavenJfrTestAttribution.Summary summary = new MavenJfrTestAttribution.Summary(
                1, 0, 3, List.of(), List.of(), List.of(evidence), List.of(), List.of()
        );

        List<String> lines = new MavenJfrRuntimeCauseExplainer().consoleLines(summary);

        assertTrue(lines.get(0).contains("CPU activity"));
        assertTrue(lines.get(0).contains("3 samples"));
        assertTrue(lines.get(0).contains("not elapsed time"));
        assertTrue(lines.get(0).contains("stack: example.FixtureFactory.create:18"));
    }

    @Test
    void retainsSampledSignalsWhenMeasuredCausesFillTheConsoleBudget() {
        MavenJfrTestAttribution.TestEvidence measured = new MavenJfrTestAttribution.TestEvidence(
                "example.SampleTest.fixedWait",
                Map.of("ThreadSleep", 1L),
                Map.of("ThreadSleep", Duration.ofMillis(900)),
                Map.of(),
                Duration.ofMillis(900)
        );
        MavenJfrTestAttribution.SampleEvidence sampled = new MavenJfrTestAttribution.SampleEvidence(
                "example.SampleTest.cpuHeavyTest", Map.of("ExecutionSample", 1L), Map.of()
        );
        MavenJfrTestAttribution.Summary summary = new MavenJfrTestAttribution.Summary(
                2, 1, 1, List.of(measured), List.of(), List.of(sampled), List.of(), List.of()
        );

        List<String> lines = new MavenJfrRuntimeCauseExplainer().consoleLines(summary);

        assertTrue(lines.stream().anyMatch(line -> line.contains("fixed wait")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("CPU activity")));
    }
}

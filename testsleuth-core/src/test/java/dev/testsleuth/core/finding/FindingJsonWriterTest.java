package dev.testsleuth.core.finding;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class FindingJsonWriterTest {
    @Test
    void writesStableJsonForFindings() {
        Finding finding = new Finding(
                new FindingId("finding-1"),
                "Slow \"quoted\" test",
                FindingCategory.BUILD_RUNNER,
                FindingSeverity.HIGH,
                Confidence.MEDIUM,
                Duration.ofMillis(1_250),
                new TimeSavingEstimate(Duration.ofMillis(100), Duration.ofMillis(1_250)),
                List.of("ExampleTest.slow"),
                List.of("Observed duration 1250 ms."),
                "Root cause text",
                "Recommended action",
                "Trade-offs",
                "Verification method"
        );

        String json = new FindingJsonWriter().write(List.of(finding));

        assertTrue(json.contains("\"id\":\"finding-1\""));
        assertTrue(json.contains("\"title\":\"Slow \\\"quoted\\\" test\""));
        assertTrue(json.contains("\"severity\":\"HIGH\""));
        assertTrue(json.contains("\"observedCostMillis\":1250"));
        assertTrue(json.contains("\"recoverableTimeLowerMillis\":100"));
        assertTrue(json.contains("\"affectedSubjects\":[\"ExampleTest.slow\"]"));
        assertTrue(json.endsWith("\n]\n"));
    }
}

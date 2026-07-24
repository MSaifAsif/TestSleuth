package dev.testsleuth.report;

import dev.testsleuth.report.HtmlReportRenderer.ReportModel;
import dev.testsleuth.core.finding.Confidence;
import dev.testsleuth.core.finding.Finding;
import dev.testsleuth.core.finding.FindingCategory;
import dev.testsleuth.core.finding.FindingId;
import dev.testsleuth.core.finding.FindingSeverity;
import dev.testsleuth.core.finding.TimeSavingEstimate;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HtmlReportRendererTest {
    @Test
    void escapesUserControlledText() {
        String html = new HtmlReportRenderer().render(new ReportModel(
                "Test <Suite>",
                "Observed & ranked",
                List.of()
        ));

        assertTrue(html.contains("Test &lt;Suite&gt;"));
        assertTrue(html.contains("Observed &amp; ranked"));
        assertFalse(html.contains("Test <Suite>"));
    }

    @Test
    void rendersScorecardAndCategoryBreakdown() {
        String html = new HtmlReportRenderer().render(new ReportModel(
                "TestSleuth Report",
                "Observed tests",
                List.of(
                        finding("Slow observed test: slowOne", FindingCategory.BUILD_RUNNER, 1_500),
                        finding("Fixed wait in test source: Example.java:10", FindingCategory.WAITING, 500)
                )
        ));

        assertTrue(html.contains("Run Summary"));
        assertTrue(html.contains("<strong>2</strong>"));
        assertTrue(html.contains("<strong>1.500 s</strong>"));
        assertTrue(html.contains("Top Opportunity"));
        assertTrue(html.contains("Slow observed test: slowOne"));
        assertTrue(html.contains("Category Breakdown"));
        assertTrue(html.contains("BUILD_RUNNER: 1"));
        assertTrue(html.contains("WAITING: 1"));
        assertTrue(html.contains("Not measured"));
    }

    private static Finding finding(String title, FindingCategory category, long observedMillis) {
        return new Finding(
                new FindingId(title.replaceAll("[^A-Za-z0-9]", "-")),
                title,
                category,
                FindingSeverity.MEDIUM,
                Confidence.MEDIUM,
                category == FindingCategory.WAITING
                        ? dev.testsleuth.core.finding.EvidenceType.POTENTIAL
                        : dev.testsleuth.core.finding.EvidenceType.MEASURED,
                category == FindingCategory.WAITING
                        ? dev.testsleuth.core.finding.AttributionScope.UNCLASSIFIED
                        : dev.testsleuth.core.finding.AttributionScope.DIRECT_TEST_THREAD,
                category == FindingCategory.WAITING ? Duration.ZERO : Duration.ofMillis(observedMillis),
                new TimeSavingEstimate(
                        Duration.ZERO,
                        category == FindingCategory.WAITING ? Duration.ZERO : Duration.ofMillis(observedMillis)
                ),
                List.of("ExampleTest"),
                List.of("Evidence."),
                "Root cause.",
                "Recommended action.",
                "Trade-offs.",
                "Verification."
        );
    }
}

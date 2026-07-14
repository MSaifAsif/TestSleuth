package dev.testsleuth.report;

import dev.testsleuth.report.HtmlReportRenderer.ReportModel;
import org.junit.jupiter.api.Test;

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
}


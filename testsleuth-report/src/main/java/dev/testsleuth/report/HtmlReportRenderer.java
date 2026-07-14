package dev.testsleuth.report;

import dev.testsleuth.core.finding.Finding;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class HtmlReportRenderer {
    public String render(ReportModel report) {
        Objects.requireNonNull(report, "report");

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<title>").append(escape(report.title())).append("</title>")
                .append("<style>body{font-family:system-ui,sans-serif;margin:2rem;line-height:1.45}")
                .append("table{border-collapse:collapse;width:100%}td,th{border:1px solid #ddd;padding:.5rem;text-align:left}")
                .append("</style></head><body>");
        html.append("<h1>").append(escape(report.title())).append("</h1>");
        html.append("<p>").append(escape(report.summary())).append("</p>");
        html.append("<table><thead><tr><th>Severity</th><th>Confidence</th><th>Finding</th><th>Action</th></tr></thead><tbody>");

        report.findings().stream()
                .sorted(Comparator.comparing(Finding::severity).reversed())
                .forEach(finding -> html.append("<tr><td>")
                        .append(escape(finding.severity().name()))
                        .append("</td><td>")
                        .append(escape(finding.confidence().name()))
                        .append("</td><td>")
                        .append(escape(finding.title()))
                        .append("</td><td>")
                        .append(escape(finding.recommendedAction()))
                        .append("</td></tr>"));

        html.append("</tbody></table></body></html>");
        return html.toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public record ReportModel(String title, String summary, List<Finding> findings) {
        public ReportModel {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(summary, "summary");
            findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        }
    }
}


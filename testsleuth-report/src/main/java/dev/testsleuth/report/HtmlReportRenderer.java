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
                .append("table{border-collapse:collapse;width:100%}td,th{border:1px solid #ddd;padding:.5rem;text-align:left;vertical-align:top}")
                .append("ul{margin:.25rem 0;padding-left:1.25rem}.muted{color:#555}")
                .append("</style></head><body>");
        html.append("<h1>").append(escape(report.title())).append("</h1>");
        html.append("<p>").append(escape(report.summary())).append("</p>");
        if (report.findings().isEmpty()) {
            html.append("<p class=\"muted\">No findings yet. TestSleuth collected events, but no detector produced a finding for this run.</p>");
        }
        html.append("<table><thead><tr><th>Severity</th><th>Confidence</th><th>Finding</th><th>Observed Cost</th><th>Evidence</th><th>Action</th></tr></thead><tbody>");

        report.findings().stream()
                .sorted(Comparator.comparing(Finding::severity).reversed())
                .forEach(finding -> html.append("<tr><td>")
                        .append(escape(finding.severity().name()))
                        .append("</td><td>")
                        .append(escape(finding.confidence().name()))
                        .append("</td><td>")
                        .append(escape(finding.title()))
                        .append("<div class=\"muted\">")
                        .append(escape(finding.rootCause()))
                        .append("</div>")
                        .append("</td><td>")
                        .append(escape(formatDuration(finding.observedCost())))
                        .append("</td><td>")
                        .append(renderEvidence(finding))
                        .append("</td><td>")
                        .append(escape(finding.recommendedAction()))
                        .append("<div class=\"muted\">")
                        .append(escape(finding.verificationMethod()))
                        .append("</div>")
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

    private static String renderEvidence(Finding finding) {
        if (finding.evidence().isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder("<ul>");
        finding.evidence().forEach(item -> html.append("<li>").append(escape(item)).append("</li>"));
        html.append("</ul>");
        return html.toString();
    }

    private static String formatDuration(java.time.Duration duration) {
        long millis = duration.toMillis();
        if (millis >= 1_000) {
            return String.format("%.3f s", millis / 1_000.0);
        }
        return millis + " ms";
    }

    public record ReportModel(String title, String summary, List<Finding> findings) {
        public ReportModel {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(summary, "summary");
            findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        }
    }
}

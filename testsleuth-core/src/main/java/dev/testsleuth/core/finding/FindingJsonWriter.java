package dev.testsleuth.core.finding;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class FindingJsonWriter {
    public String write(List<Finding> findings) {
        Objects.requireNonNull(findings, "findings");

        return findings.stream()
                .map(this::writeFinding)
                .collect(Collectors.joining(",\n", "[\n", "\n]\n"));
    }

    private String writeFinding(Finding finding) {
        return "  {"
                + field("id", finding.id().value())
                + field("title", finding.title())
                + field("category", finding.category().name())
                + field("severity", finding.severity().name())
                + field("confidence", finding.confidence().name())
                + field("evidenceType", finding.evidenceType().name())
                + field("attributionScope", finding.attributionScope().name())
                + numericField("observedCostMillis", finding.observedCost().toMillis())
                + numericField("recoverableTimeLowerMillis", finding.recoverableTime().lowerBound().toMillis())
                + numericField("recoverableTimeUpperMillis", finding.recoverableTime().upperBound().toMillis())
                + arrayField("affectedSubjects", finding.affectedSubjects())
                + arrayField("evidence", finding.evidence())
                + field("rootCause", finding.rootCause())
                + field("recommendedAction", finding.recommendedAction())
                + field("tradeOffs", finding.tradeOffs())
                + fieldWithoutComma("verificationMethod", finding.verificationMethod())
                + "}";
    }

    private static String field(String name, String value) {
        return "\"" + escape(name) + "\":\"" + escape(value) + "\",";
    }

    private static String fieldWithoutComma(String name, String value) {
        return "\"" + escape(name) + "\":\"" + escape(value) + "\"";
    }

    private static String numericField(String name, long value) {
        return "\"" + escape(name) + "\":" + value + ",";
    }

    private static String arrayField(String name, List<String> values) {
        return "\"" + escape(name) + "\":["
                + values.stream()
                .map(value -> "\"" + escape(value) + "\"")
                .collect(Collectors.joining(","))
                + "],";
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}

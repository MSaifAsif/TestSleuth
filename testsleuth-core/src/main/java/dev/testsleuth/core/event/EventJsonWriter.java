package dev.testsleuth.core.event;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class EventJsonWriter {
    public String write(List<TestSleuthEvent> events) {
        Objects.requireNonNull(events, "events");

        return events.stream()
                .map(this::writeEvent)
                .collect(Collectors.joining(",\n", "[\n", "\n]\n"));
    }

    private String writeEvent(TestSleuthEvent event) {
        return "  {"
                + field("id", event.id().value())
                + optionalField("parentId", event.parentId().map(EventId::value).orElse(null))
                + field("kind", event.kind().name())
                + field("subjectType", event.subject().type().name())
                + field("subjectId", event.subject().identifier())
                + field("wallClockTime", event.wallClockTime().toString())
                + numericField("monotonicNanos", event.monotonicNanos())
                + attributesField(event.attributes())
                + "}";
    }

    private static String field(String name, String value) {
        return "\"" + escape(name) + "\":\"" + escape(value) + "\",";
    }

    private static String optionalField(String name, String value) {
        if (value == null) {
            return "\"" + escape(name) + "\":null,";
        }
        return field(name, value);
    }

    private static String numericField(String name, long value) {
        return "\"" + escape(name) + "\":" + value + ",";
    }

    private static String attributesField(Map<String, String> attributes) {
        return "\"attributes\":{"
                + attributes.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> "\"" + escape(entry.getKey()) + "\":\"" + escape(entry.getValue()) + "\"")
                .collect(Collectors.joining(","))
                + "}";
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


package dev.testsleuth.core.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class EventJsonReader {
    public List<TestSleuthEvent> read(String json) {
        Objects.requireNonNull(json, "json");
        Parser parser = new Parser(json);
        List<Map<String, Object>> objects = parser.array();
        parser.end();

        return objects.stream()
                .map(EventJsonReader::toEvent)
                .toList();
    }

    private static TestSleuthEvent toEvent(Map<String, Object> object) {
        return new TestSleuthEvent(
                new EventId(requiredString(object, "id")),
                optionalString(object, "parentId").map(EventId::new),
                EventKind.valueOf(requiredString(object, "kind")),
                new Subject(
                        SubjectType.valueOf(requiredString(object, "subjectType")),
                        requiredString(object, "subjectId")
                ),
                Instant.parse(requiredString(object, "wallClockTime")),
                requiredLong(object, "monotonicNanos"),
                stringMap(object, "attributes")
        );
    }

    private static String requiredString(Map<String, Object> object, String fieldName) {
        Object value = object.get(fieldName);
        if (value instanceof String text) {
            return text;
        }
        throw new IllegalArgumentException("Expected string field " + fieldName);
    }

    private static Optional<String> optionalString(Map<String, Object> object, String fieldName) {
        Object value = object.get(fieldName);
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof String text) {
            return Optional.of(text);
        }
        throw new IllegalArgumentException("Expected optional string field " + fieldName);
    }

    private static long requiredLong(Map<String, Object> object, String fieldName) {
        Object value = object.get(fieldName);
        if (value instanceof Long number) {
            return number;
        }
        throw new IllegalArgumentException("Expected numeric field " + fieldName);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> stringMap(Map<String, Object> object, String fieldName) {
        Object value = object.get(fieldName);
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException("Expected object field " + fieldName);
        }

        Map<String, String> values = new LinkedHashMap<>();
        rawMap.forEach((key, mapValue) -> {
            if (!(key instanceof String textKey) || !(mapValue instanceof String textValue)) {
                throw new IllegalArgumentException("Expected string map field " + fieldName);
            }
            values.put(textKey, textValue);
        });
        return values;
    }

    private static final class Parser {
        private final String json;
        private int index;

        private Parser(String json) {
            this.json = json;
        }

        private List<Map<String, Object>> array() {
            skipWhitespace();
            expect('[');
            List<Map<String, Object>> objects = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                index++;
                return objects;
            }

            while (true) {
                objects.add(object());
                skipWhitespace();
                if (peek(',')) {
                    index++;
                    continue;
                }
                expect(']');
                return objects;
            }
        }

        private Map<String, Object> object() {
            skipWhitespace();
            expect('{');
            Map<String, Object> values = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                index++;
                return values;
            }

            while (true) {
                String key = string();
                skipWhitespace();
                expect(':');
                values.put(key, value());
                skipWhitespace();
                if (peek(',')) {
                    index++;
                    continue;
                }
                expect('}');
                return values;
            }
        }

        private Object value() {
            skipWhitespace();
            if (peek('"')) {
                return string();
            }
            if (peek('{')) {
                return object();
            }
            if (startsWith("null")) {
                index += 4;
                return null;
            }
            return number();
        }

        private String string() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (index < json.length()) {
                char character = json.charAt(index++);
                if (character == '"') {
                    return value.toString();
                }
                if (character == '\\') {
                    value.append(escapedCharacter());
                } else {
                    value.append(character);
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private char escapedCharacter() {
            if (index >= json.length()) {
                throw new IllegalArgumentException("Unterminated JSON escape");
            }
            char escaped = json.charAt(index++);
            return switch (escaped) {
                case '"' -> '"';
                case '\\' -> '\\';
                case '/' -> '/';
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'u' -> unicodeEscape();
                default -> throw new IllegalArgumentException("Unsupported JSON escape \\" + escaped);
            };
        }

        private char unicodeEscape() {
            if (index + 4 > json.length()) {
                throw new IllegalArgumentException("Invalid JSON unicode escape");
            }
            String hex = json.substring(index, index + 4);
            index += 4;
            return (char) Integer.parseInt(hex, 16);
        }

        private Long number() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            while (index < json.length() && Character.isDigit(json.charAt(index))) {
                index++;
            }
            if (start == index || (json.charAt(start) == '-' && start + 1 == index)) {
                throw new IllegalArgumentException("Expected JSON value at offset " + index);
            }
            return Long.parseLong(json.substring(start, index));
        }

        private void end() {
            skipWhitespace();
            if (index != json.length()) {
                throw new IllegalArgumentException("Unexpected JSON content at offset " + index);
            }
        }

        private void expect(char expected) {
            skipWhitespace();
            if (index >= json.length() || json.charAt(index) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at offset " + index);
            }
            index++;
        }

        private boolean peek(char expected) {
            return index < json.length() && json.charAt(index) == expected;
        }

        private boolean startsWith(String value) {
            return json.startsWith(value, index);
        }

        private void skipWhitespace() {
            while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
                index++;
            }
        }
    }
}

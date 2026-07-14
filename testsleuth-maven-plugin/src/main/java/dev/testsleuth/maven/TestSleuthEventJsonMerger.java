package dev.testsleuth.maven;

import dev.testsleuth.core.event.EventJsonWriter;
import dev.testsleuth.core.event.TestSleuthEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class TestSleuthEventJsonMerger {
    EventJson merge(Path existingEventsFile, List<TestSleuthEvent> additionalEvents) {
        Objects.requireNonNull(existingEventsFile, "existingEventsFile");
        Objects.requireNonNull(additionalEvents, "additionalEvents");

        String existingJson = readExistingEvents(existingEventsFile);
        String additionalJson = new EventJsonWriter().write(additionalEvents);

        List<String> arrays = new ArrayList<>();
        if (!arrayBody(existingJson).isBlank()) {
            arrays.add(arrayBody(existingJson));
        }
        if (!arrayBody(additionalJson).isBlank()) {
            arrays.add(arrayBody(additionalJson));
        }

        String json = arrays.isEmpty()
                ? "[\n]\n"
                : arrays.stream().collect(java.util.stream.Collectors.joining(",\n", "[\n", "\n]\n"));
        return new EventJson(json, countEvents(existingJson));
    }

    private static String readExistingEvents(Path eventsFile) {
        if (!Files.isRegularFile(eventsFile)) {
            return "[\n]\n";
        }
        try {
            return Files.readString(eventsFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read TestSleuth event file " + eventsFile, e);
        }
    }

    private static String arrayBody(String json) {
        String trimmed = json.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '[' || trimmed.charAt(trimmed.length() - 1) != ']') {
            throw new IllegalArgumentException("Expected a JSON array of TestSleuth events");
        }
        return trimmed.substring(1, trimmed.length() - 1).trim();
    }

    private static int countEvents(String json) {
        String body = arrayBody(json);
        if (body.isBlank()) {
            return 0;
        }

        int count = 0;
        int index = 0;
        while ((index = body.indexOf("\"id\"", index)) >= 0) {
            count++;
            index += 4;
        }
        return count;
    }

    record EventJson(String json, int preexistingEventCount) {
    }
}


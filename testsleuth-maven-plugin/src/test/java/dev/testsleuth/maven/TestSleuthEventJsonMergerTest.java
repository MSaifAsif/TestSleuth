package dev.testsleuth.maven;

import dev.testsleuth.core.event.EventId;
import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.Subject;
import dev.testsleuth.core.event.SubjectType;
import dev.testsleuth.core.event.TestSleuthEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TestSleuthEventJsonMergerTest {
    @TempDir
    private Path tempDir;

    @Test
    void mergesExistingJsonArrayWithAdditionalEvents() throws Exception {
        Path existingEvents = tempDir.resolve("junit-events.json");
        Files.writeString(existingEvents, """
                [
                  {"id":"existing","parentId":null,"kind":"TEST_STARTED","subjectType":"TEST_METHOD","subjectId":"test","wallClockTime":"2026-07-14T00:00:00Z","monotonicNanos":1,"attributes":{}}
                ]
                """);

        TestSleuthEvent additional = new TestSleuthEvent(
                new EventId("additional"),
                Optional.empty(),
                EventKind.TEST_FINISHED,
                new Subject(SubjectType.TEST_METHOD, "test"),
                Instant.parse("2026-07-14T00:00:01Z"),
                2,
                Map.of("status", "passed")
        );

        TestSleuthEventJsonMerger.EventJson merged = new TestSleuthEventJsonMerger()
                .merge(existingEvents, List.of(additional));

        assertEquals(1, merged.preexistingEventCount());
        assertTrue(merged.json().contains("\"id\":\"existing\""));
        assertTrue(merged.json().contains("\"id\":\"additional\""));
        assertEquals(1, new TestSleuthEventJsonMerger().readEvents(existingEvents).size());
    }

    @Test
    void handlesMissingExistingFile() {
        TestSleuthEventJsonMerger.EventJson merged = new TestSleuthEventJsonMerger()
                .merge(tempDir.resolve("missing.json"), List.of());

        assertEquals(0, merged.preexistingEventCount());
        assertEquals("[\n]\n", merged.json());
    }

    @Test
    void mergesMultipleEventFiles() throws Exception {
        Path first = tempDir.resolve("first.json");
        Path second = tempDir.resolve("second.json");
        Files.writeString(first, """
                [
                  {"id":"first","attributes":{"collector":"junit5-listener"}}
                ]
                """);
        Files.writeString(second, """
                [
                  {"id":"second","attributes":{"collector":"junit4-listener"}}
                ]
                """);

        TestSleuthEventJsonMerger merger = new TestSleuthEventJsonMerger();
        TestSleuthEventJsonMerger.EventJson merged = merger.mergeEventFiles(List.of(first, second));

        assertEquals(2, merged.preexistingEventCount());
        assertTrue(merged.json().contains("\"id\":\"first\""));
        assertTrue(merged.json().contains("\"id\":\"second\""));
        assertEquals(1, merger.countAttributeValue(merged.json(), "collector", "junit5-listener"));
        assertEquals(1, merger.countAttributeValue(merged.json(), "collector", "junit4-listener"));
    }

    @Test
    void mergesEventFilesWithAdditionalEvents() throws Exception {
        Path first = tempDir.resolve("first.json");
        Files.writeString(first, """
                [
                  {"id":"first","attributes":{"collector":"junit5-listener"}}
                ]
                """);
        TestSleuthEvent additional = new TestSleuthEvent(
                new EventId("additional"),
                Optional.empty(),
                EventKind.TEST_FINISHED,
                new Subject(SubjectType.TEST_METHOD, "test"),
                Instant.parse("2026-07-14T00:00:01Z"),
                2,
                Map.of("collector", "maven-test-report")
        );

        TestSleuthEventJsonMerger.EventJson merged = new TestSleuthEventJsonMerger()
                .mergeEventFiles(List.of(first), List.of(additional));

        assertTrue(merged.json().contains("\"id\":\"first\""));
        assertTrue(merged.json().contains("\"id\":\"additional\""));
    }
}

package dev.testsleuth.maven;

import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MavenJfrRecordingReader {
    private static final Map<String, String> SELECTED_EVENT_FAMILIES = Map.ofEntries(
            Map.entry("jdk.ThreadSleep", "ThreadSleep"),
            Map.entry("jdk.ThreadPark", "ThreadPark"),
            Map.entry("jdk.JavaMonitorEnter", "MonitorEnter"),
            Map.entry("jdk.SocketRead", "SocketRead"),
            Map.entry("jdk.SocketWrite", "SocketWrite"),
            Map.entry("jdk.FileRead", "FileRead"),
            Map.entry("jdk.FileWrite", "FileWrite"),
            Map.entry("jdk.GarbageCollection", "GarbageCollection"),
            Map.entry("jdk.ExecutionSample", "ExecutionSample"),
            Map.entry("jdk.ObjectAllocationSample", "ObjectAllocationSample"),
            Map.entry("jdk.ClassLoad", "ClassLoad"),
            Map.entry("dev.testsleuth.TestLifecycle", "TestLifecycle")
    );

    Summary summarize(Path recordingsDirectory, boolean enabled) {
        if (!enabled || !Files.isDirectory(recordingsDirectory)) {
            return Summary.disabled();
        }

        int recordingCount = 0;
        int parsedRecordings = 0;
        int unreadableRecordings = 0;
        long totalEvents = 0L;
        Map<String, Long> selectedEvents = new LinkedHashMap<>();
        try (var paths = Files.list(recordingsDirectory)) {
            List<Path> recordings = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jfr"))
                    .sorted()
                    .toList();
            recordingCount = recordings.size();
            for (Path recording : recordings) {
                try (RecordingFile file = new RecordingFile(recording)) {
                    while (file.hasMoreEvents()) {
                        var event = file.readEvent();
                        totalEvents++;
                        String family = SELECTED_EVENT_FAMILIES.get(event.getEventType().getName());
                        if (family != null) {
                            selectedEvents.merge(family, 1L, Long::sum);
                        }
                    }
                    parsedRecordings++;
                } catch (IOException | RuntimeException ignored) {
                    unreadableRecordings++;
                }
            }
        } catch (IOException ignored) {
            return new Summary(true, recordingCount, parsedRecordings, recordingCount + 1, totalEvents, selectedEvents);
        }
        return new Summary(true, recordingCount, parsedRecordings, unreadableRecordings, totalEvents, selectedEvents);
    }

    record Summary(
            boolean enabled,
            int recordingCount,
            int parsedRecordings,
            int unreadableRecordings,
            long totalEvents,
            Map<String, Long> selectedEvents
    ) {
        Summary {
            selectedEvents = Map.copyOf(selectedEvents);
        }

        static Summary disabled() {
            return new Summary(false, 0, 0, 0, 0L, Map.of());
        }

        String consoleLine() {
            if (recordingCount == 0) {
                return "[TestSleuth] JFR event evidence: no recordings available for parsing";
            }
            StringBuilder line = new StringBuilder("[TestSleuth] JFR event evidence: ")
                    .append(parsedRecordings).append('/').append(recordingCount)
                    .append(" recordings parsed, ").append(totalEvents).append(" events");
            if (!selectedEvents.isEmpty()) {
                line.append("; ");
                selectedEvents.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> line.append(entry.getKey()).append('=').append(entry.getValue()).append(", "));
                line.setLength(line.length() - 2);
            }
            if (unreadableRecordings > 0) {
                line.append("; ").append(unreadableRecordings).append(" unreadable");
            }
            return line.toString();
        }

        String reportSentence() {
            if (!enabled || recordingCount == 0) {
                return "";
            }
            return "Parsed " + parsedRecordings + " of " + recordingCount + " JFR recordings ("
                    + totalEvents + " events); runtime event attribution is pending. ";
        }
    }
}

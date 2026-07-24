package dev.testsleuth.maven;

import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;

final class MavenJfrRecording {
    static final String ENABLED_PROPERTY = "testsleuth.jfr.enabled";
    static final String RECORDINGS_DIRECTORY_NAME = "jfr";
    private static final String FORK_NUMBER = "${surefire.forkNumber}";
    private static final String PROCESS_ID = "%p";
    private static final String START_RECORDING_PREFIX = "-XX:StartFlightRecording=";
    private static final String JFR_STARTUP_LOGGING_ARGUMENT = "-Xlog:jfr+startup=off";

    Path recordingsDirectory(Path outputDirectory) {
        return outputDirectory.resolve(RECORDINGS_DIRECTORY_NAME);
    }

    void reset(Path outputDirectory) {
        Path recordingsDirectory = recordingsDirectory(outputDirectory);
        try {
            if (Files.isDirectory(recordingsDirectory)) {
                try (var paths = Files.walk(recordingsDirectory)) {
                    paths.sorted(Comparator.reverseOrder()).forEach(this::delete);
                }
            }
            Files.createDirectories(recordingsDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare TestSleuth JFR recording directory " + recordingsDirectory, e);
        }
    }

    String recordingArgument(Path recordingsDirectory, String runner) {
        Objects.requireNonNull(recordingsDirectory, "recordingsDirectory");
        requireText(runner, "runner");
        Path recording = recordingsDirectory.resolve(runner + "-" + FORK_NUMBER + ".jfr");
        return START_RECORDING_PREFIX
                + "filename=" + recording
                + ",disk=true,dumponexit=true,maxsize=32m,settings=default "
                + JFR_STARTUP_LOGGING_ARGUMENT;
    }

    String recordingArgumentForProcess(Path recordingsDirectory) {
        Objects.requireNonNull(recordingsDirectory, "recordingsDirectory");
        Path recording = recordingsDirectory.resolve("testsleuth-" + PROCESS_ID + ".jfr");
        return START_RECORDING_PREFIX
                + "filename=" + recording
                + ",disk=true,dumponexit=true,maxsize=32m,settings=default "
                + JFR_STARTUP_LOGGING_ARGUMENT;
    }

    boolean appendRecordingArgument(Xpp3Dom configuration, Path recordingsDirectory, String runner) {
        Objects.requireNonNull(configuration, "configuration");
        String existing = childValue(configuration, "argLine");
        if (existing.contains(START_RECORDING_PREFIX)) {
            return false;
        }

        Xpp3Dom argLine = child(configuration, "argLine");
        String recordingArgument = recordingArgument(recordingsDirectory, runner);
        argLine.setValue(existing.isBlank() ? recordingArgument : existing + " " + recordingArgument);
        return true;
    }

    Summary summarize(Path outputDirectory, boolean enabled) {
        if (!enabled) {
            return new Summary(false, 0, 0L, recordingsDirectory(outputDirectory));
        }

        Path recordingsDirectory = recordingsDirectory(outputDirectory);
        try {
            if (!Files.isDirectory(recordingsDirectory)) {
                return new Summary(true, 0, 0L, recordingsDirectory);
            }
            try (var paths = Files.list(recordingsDirectory)) {
                var recordings = paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".jfr"))
                        .toList();
                long bytes = 0L;
                for (Path recording : recordings) {
                    bytes += Files.size(recording);
                }
                return new Summary(true, recordings.size(), bytes, recordingsDirectory);
            }
        } catch (IOException e) {
            return new Summary(true, 0, 0L, recordingsDirectory);
        }
    }

    private static Xpp3Dom child(Xpp3Dom parent, String name) {
        Xpp3Dom child = parent.getChild(name);
        if (child == null) {
            child = new Xpp3Dom(name);
            parent.addChild(child);
        }
        return child;
    }

    private static String childValue(Xpp3Dom parent, String name) {
        Xpp3Dom child = parent.getChild(name);
        return child == null || child.getValue() == null ? "" : child.getValue().trim();
    }

    private void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to reset TestSleuth JFR recording " + path, e);
        }
    }

    private static void requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    record Summary(boolean enabled, int recordingCount, long recordingBytes, Path recordingsDirectory) {
        Summary {
            Objects.requireNonNull(recordingsDirectory, "recordingsDirectory");
        }

        String consoleLine() {
            if (recordingCount == 0) {
                return "[TestSleuth] JFR recordings: none found at " + recordingsDirectory;
            }
            return "[TestSleuth] JFR recordings: " + recordingCount + " files, "
                    + recordingBytes + " bytes at " + recordingsDirectory;
        }

        String reportSentence() {
            if (!enabled) {
                return "";
            }
            if (recordingCount == 0) {
                return "JFR capture enabled, but no recordings were found. ";
            }
            return "JFR capture retained " + recordingCount + " raw recordings ("
                    + recordingBytes + " bytes); parsing and test-level attribution are pending. ";
        }
    }
}

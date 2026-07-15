package dev.testsleuth.maven;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

final class MavenBuildTiming {
    static final String TIMING_FILE_NAME = "run-timing.properties";
    private static final String STARTED_AT_PROPERTY = "testsleuth.run.startedAt";
    private static final String STARTED_NANOS_PROPERTY = "testsleuth.run.startedNanos";

    RunTiming start(Path outputDirectory) {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        RunTiming timing = new RunTiming(Instant.now(), System.nanoTime());
        Properties properties = new Properties();
        properties.setProperty(STARTED_AT_PROPERTY, timing.startedAt().toString());
        properties.setProperty(STARTED_NANOS_PROPERTY, Long.toString(timing.startedNanos()));

        try {
            Files.createDirectories(outputDirectory);
            try (Writer writer = Files.newBufferedWriter(
                    outputDirectory.resolve(TIMING_FILE_NAME),
                    StandardCharsets.UTF_8
            )) {
                properties.store(writer, "TestSleuth run timing");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write TestSleuth run timing to " + outputDirectory, e);
        }
        return timing;
    }

    Optional<RunTiming> load(Path outputDirectory) {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Path timingFile = outputDirectory.resolve(TIMING_FILE_NAME);
        if (!Files.isRegularFile(timingFile)) {
            return Optional.empty();
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(timingFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read TestSleuth run timing " + timingFile, e);
        }

        String startedAt = properties.getProperty(STARTED_AT_PROPERTY);
        String startedNanos = properties.getProperty(STARTED_NANOS_PROPERTY);
        if (startedAt == null || startedAt.isBlank() || startedNanos == null || startedNanos.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new RunTiming(Instant.parse(startedAt), Long.parseLong(startedNanos)));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    record RunTiming(Instant startedAt, long startedNanos) {
        RunTiming {
            Objects.requireNonNull(startedAt, "startedAt");
            if (startedNanos < 0) {
                throw new IllegalArgumentException("started nanos must not be negative");
            }
        }

        Duration elapsedSinceStart() {
            long elapsedNanos = System.nanoTime() - startedNanos;
            if (elapsedNanos < 0) {
                return Duration.ZERO;
            }
            return Duration.ofNanos(elapsedNanos);
        }
    }
}

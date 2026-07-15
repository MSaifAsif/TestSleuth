package dev.testsleuth.maven;

import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.TestSleuthEvent;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

record MavenTimingSummary(
        Optional<Duration> lifecycleWindow,
        Duration mavenReportedTestTime,
        Duration junitObservedTestTime,
        Duration junitSetupTime,
        Duration junitTeardownTime,
        Optional<Duration> unaccountedLifecycleTime
) {
    MavenTimingSummary {
        lifecycleWindow = Objects.requireNonNull(lifecycleWindow, "lifecycleWindow");
        Objects.requireNonNull(mavenReportedTestTime, "mavenReportedTestTime");
        Objects.requireNonNull(junitObservedTestTime, "junitObservedTestTime");
        Objects.requireNonNull(junitSetupTime, "junitSetupTime");
        Objects.requireNonNull(junitTeardownTime, "junitTeardownTime");
        unaccountedLifecycleTime = Objects.requireNonNull(unaccountedLifecycleTime, "unaccountedLifecycleTime");
    }

    static MavenTimingSummary from(Optional<Duration> lifecycleWindow, List<TestSleuthEvent> events) {
        Objects.requireNonNull(lifecycleWindow, "lifecycleWindow");
        Objects.requireNonNull(events, "events");

        Duration mavenReportedTestTime = totalFinishedDuration(events, "maven-test-report");
        Duration junitObservedTestTime = totalFinishedDuration(events, "junit5-listener");
        Duration junitSetupTime = totalDuration(events, EventKind.SETUP_FINISHED, "junit5-listener");
        Duration junitTeardownTime = totalDuration(events, EventKind.TEARDOWN_FINISHED, "junit5-listener");
        Optional<Duration> unaccountedLifecycleTime = lifecycleWindow
                .map(window -> window.minus(mavenReportedTestTime))
                .map(duration -> duration.isNegative() ? Duration.ZERO : duration);

        return new MavenTimingSummary(
                lifecycleWindow,
                mavenReportedTestTime,
                junitObservedTestTime,
                junitSetupTime,
                junitTeardownTime,
                unaccountedLifecycleTime
        );
    }

    String consoleLine() {
        StringBuilder line = new StringBuilder("[TestSleuth] Timing: Maven tests ")
                .append(mavenReportedTestTime.toMillis())
                .append(" ms, JUnit observed ")
                .append(junitObservedTestTime.toMillis())
                .append(" ms, setup ")
                .append(junitSetupTime.toMillis())
                .append(" ms, teardown ")
                .append(junitTeardownTime.toMillis())
                .append(" ms");
        unaccountedLifecycleTime.ifPresent(duration -> line
                .append(", lifecycle remainder ")
                .append(duration.toMillis())
                .append(" ms"));
        return line.toString();
    }

    String reportSentence() {
        StringBuilder sentence = new StringBuilder("Maven-reported test time ")
                .append(mavenReportedTestTime.toMillis())
                .append(" ms; JUnit-observed test time ")
                .append(junitObservedTestTime.toMillis())
                .append(" ms; setup time ")
                .append(junitSetupTime.toMillis())
                .append(" ms; teardown time ")
                .append(junitTeardownTime.toMillis())
                .append(" ms");
        unaccountedLifecycleTime.ifPresent(duration -> sentence
                .append("; lifecycle remainder ")
                .append(duration.toMillis())
                .append(" ms"));
        return sentence.append(". ").toString();
    }

    private static Duration totalFinishedDuration(List<TestSleuthEvent> events, String collector) {
        return totalDuration(events, EventKind.TEST_FINISHED, collector);
    }

    private static Duration totalDuration(List<TestSleuthEvent> events, EventKind kind, String collector) {
        long totalMillis = events.stream()
                .filter(event -> event.kind() == kind)
                .filter(event -> collector.equals(event.attributes().get("collector")))
                .mapToLong(MavenTimingSummary::durationMillis)
                .sum();
        return Duration.ofMillis(totalMillis);
    }

    private static long durationMillis(TestSleuthEvent event) {
        try {
            return Math.max(0, Long.parseLong(event.attributes().getOrDefault("durationMillis", "0")));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

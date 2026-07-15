package dev.testsleuth.maven;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

record TestSleuthMavenConfig(
        boolean consoleEnabled,
        ConsoleDetail consoleDetail,
        Duration slowTestThreshold,
        Duration verySlowTestThreshold,
        Duration fixedWaitThreshold,
        Duration pollingWaitThreshold,
        int maxFindings,
        boolean slowTestsDetectorEnabled,
        boolean fixedWaitsDetectorEnabled,
        boolean pollingWaitsDetectorEnabled
) {
    static final boolean DEFAULT_CONSOLE_ENABLED = true;
    static final ConsoleDetail DEFAULT_CONSOLE_DETAIL = ConsoleDetail.SUMMARY;
    static final long DEFAULT_SLOW_TEST_MILLIS = 1_000;
    static final long DEFAULT_VERY_SLOW_TEST_MILLIS = 5_000;
    static final long DEFAULT_FIXED_WAIT_MILLIS = 250;
    static final long DEFAULT_POLLING_WAIT_MILLIS = 100;
    static final int DEFAULT_MAX_FINDINGS = 10;
    static final boolean DEFAULT_SLOW_TESTS_DETECTOR_ENABLED = true;
    static final boolean DEFAULT_FIXED_WAITS_DETECTOR_ENABLED = false;
    static final boolean DEFAULT_POLLING_WAITS_DETECTOR_ENABLED = false;

    TestSleuthMavenConfig {
        Objects.requireNonNull(consoleDetail, "consoleDetail");
        Objects.requireNonNull(slowTestThreshold, "slowTestThreshold");
        Objects.requireNonNull(verySlowTestThreshold, "verySlowTestThreshold");
        Objects.requireNonNull(fixedWaitThreshold, "fixedWaitThreshold");
        Objects.requireNonNull(pollingWaitThreshold, "pollingWaitThreshold");
        if (slowTestThreshold.isNegative()) {
            throw new IllegalArgumentException("slow test threshold must not be negative");
        }
        if (verySlowTestThreshold.compareTo(slowTestThreshold) < 0) {
            throw new IllegalArgumentException("very slow test threshold must be greater than or equal to slow test threshold");
        }
        if (fixedWaitThreshold.isNegative()) {
            throw new IllegalArgumentException("fixed wait threshold must not be negative");
        }
        if (pollingWaitThreshold.isNegative()) {
            throw new IllegalArgumentException("polling wait threshold must not be negative");
        }
        if (maxFindings < 0) {
            throw new IllegalArgumentException("max findings must not be negative");
        }
    }

    static TestSleuthMavenConfig from(
            boolean consoleEnabled,
            String consoleDetail,
            long slowTestMillis,
            long verySlowTestMillis,
            int maxFindings,
            boolean slowTestsDetectorEnabled,
            boolean fixedWaitsDetectorEnabled,
            long fixedWaitMillis
    ) {
        return from(
                consoleEnabled,
                consoleDetail,
                slowTestMillis,
                verySlowTestMillis,
                maxFindings,
                slowTestsDetectorEnabled,
                fixedWaitsDetectorEnabled,
                fixedWaitMillis,
                DEFAULT_POLLING_WAITS_DETECTOR_ENABLED,
                DEFAULT_POLLING_WAIT_MILLIS
        );
    }

    static TestSleuthMavenConfig from(
            boolean consoleEnabled,
            String consoleDetail,
            long slowTestMillis,
            long verySlowTestMillis,
            int maxFindings,
            boolean slowTestsDetectorEnabled,
            boolean fixedWaitsDetectorEnabled,
            long fixedWaitMillis,
            boolean pollingWaitsDetectorEnabled,
            long pollingWaitMillis
    ) {
        return new TestSleuthMavenConfig(
                consoleEnabled,
                ConsoleDetail.parse(consoleDetail),
                Duration.ofMillis(slowTestMillis),
                Duration.ofMillis(verySlowTestMillis),
                Duration.ofMillis(fixedWaitMillis),
                Duration.ofMillis(pollingWaitMillis),
                maxFindings,
                slowTestsDetectorEnabled,
                fixedWaitsDetectorEnabled,
                pollingWaitsDetectorEnabled
        );
    }

    enum ConsoleDetail {
        QUIET,
        SUMMARY,
        FINDINGS;

        static ConsoleDetail parse(String value) {
            if (value == null || value.isBlank()) {
                return DEFAULT_CONSOLE_DETAIL;
            }
            return ConsoleDetail.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }
}

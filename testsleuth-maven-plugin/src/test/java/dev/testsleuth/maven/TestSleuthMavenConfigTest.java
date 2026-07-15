package dev.testsleuth.maven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TestSleuthMavenConfigTest {
    @Test
    void parsesConsoleDetailCaseInsensitively() {
        TestSleuthMavenConfig config = TestSleuthMavenConfig.from(true, "findings", 1_000, 5_000, 10, true, false, 250);

        assertEquals(TestSleuthMavenConfig.ConsoleDetail.FINDINGS, config.consoleDetail());
    }

    @Test
    void rejectsInvalidThresholds() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TestSleuthMavenConfig.from(true, "summary", 5_000, 1_000, 10, true, false, 250)
        );

        assertEquals("very slow test threshold must be greater than or equal to slow test threshold", exception.getMessage());
    }

    @Test
    void rejectsInvalidFixedWaitThreshold() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TestSleuthMavenConfig.from(true, "summary", 1_000, 5_000, 10, true, false, -1)
        );

        assertEquals("fixed wait threshold must not be negative", exception.getMessage());
    }

    @Test
    void rejectsInvalidPollingWaitThreshold() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TestSleuthMavenConfig.from(true, "summary", 1_000, 5_000, 10, true, false, 250, false, -1)
        );

        assertEquals("polling wait threshold must not be negative", exception.getMessage());
    }
}

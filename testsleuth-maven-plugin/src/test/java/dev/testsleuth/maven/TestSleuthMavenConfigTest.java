package dev.testsleuth.maven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TestSleuthMavenConfigTest {
    @Test
    void parsesConsoleDetailCaseInsensitively() {
        TestSleuthMavenConfig config = TestSleuthMavenConfig.from(true, "findings", 1_000, 5_000, 10, true);

        assertEquals(TestSleuthMavenConfig.ConsoleDetail.FINDINGS, config.consoleDetail());
    }

    @Test
    void rejectsInvalidThresholds() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TestSleuthMavenConfig.from(true, "summary", 5_000, 1_000, 10, true)
        );

        assertEquals("very slow test threshold must be greater than or equal to slow test threshold", exception.getMessage());
    }
}


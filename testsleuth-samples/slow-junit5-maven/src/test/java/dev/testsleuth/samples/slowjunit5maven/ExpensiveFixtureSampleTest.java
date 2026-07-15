package dev.testsleuth.samples.slowjunit5maven;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExpensiveFixtureSampleTest {
    @BeforeAll
    static void classScopedFixture() throws InterruptedException {
        Thread.sleep(450);
    }

    @BeforeEach
    void repeatedFixtureCreation() throws InterruptedException {
        Thread.sleep(325);
    }

    @Test
    void fixtureDominatesSmallAssertion() {
        assertEquals("customer-123", "customer-" + 123);
    }

    @Test
    void duplicateFixtureSequence() {
        assertTrue("paid".contains("id"));
    }
}

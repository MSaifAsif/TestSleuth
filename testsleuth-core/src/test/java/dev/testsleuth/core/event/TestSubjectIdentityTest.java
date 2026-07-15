package dev.testsleuth.core.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TestSubjectIdentityTest {
    @Test
    void buildsCanonicalTestMethodIdentity() {
        assertEquals(
                "dev.testsleuth.ExampleTest.passes",
                TestSubjectIdentity.testMethod(" dev.testsleuth.ExampleTest ", " passes ")
        );
    }

    @Test
    void removesDisplayNameParameterSuffixFromMethodName() {
        assertEquals("parameterized", TestSubjectIdentity.normalizeMethodName("parameterized(int)[1]"));
    }

    @Test
    void rejectsBlankValues() {
        assertThrows(IllegalArgumentException.class, () -> TestSubjectIdentity.testMethod(" ", "passes"));
        assertThrows(IllegalArgumentException.class, () -> TestSubjectIdentity.testMethod("ExampleTest", " "));
    }
}

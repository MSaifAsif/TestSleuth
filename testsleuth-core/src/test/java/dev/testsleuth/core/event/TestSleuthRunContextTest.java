package dev.testsleuth.core.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TestSleuthRunContextTest {
    @Test
    void exposesRunContextAsEventAttributes() {
        TestSleuthRunContext context = new TestSleuthRunContext(
                "run-1",
                "module-a",
                "dev.testsleuth",
                "sample",
                "0.1.0",
                "/workspace/sample",
                "123",
                "1"
        );

        assertEquals("run-1", context.attributes().get("buildRunId"));
        assertEquals("module-a", context.attributes().get("moduleId"));
        assertEquals("dev.testsleuth", context.attributes().get("projectGroupId"));
        assertEquals("sample", context.attributes().get("projectArtifactId"));
        assertEquals("0.1.0", context.attributes().get("projectVersion"));
        assertEquals("/workspace/sample", context.attributes().get("projectBaseDir"));
        assertEquals("123", context.attributes().get("processId"));
        assertEquals("1", context.attributes().get("forkNumber"));
    }

    @Test
    void normalizesMissingValuesToUnknown() {
        TestSleuthRunContext context = new TestSleuthRunContext(null, " ", null, null, null, null, null, null);

        assertEquals(TestSleuthRunContext.UNKNOWN, context.attributes().get("buildRunId"));
        assertEquals(TestSleuthRunContext.UNKNOWN, context.attributes().get("moduleId"));
    }
}

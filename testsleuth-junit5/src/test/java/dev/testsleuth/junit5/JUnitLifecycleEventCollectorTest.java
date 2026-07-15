package dev.testsleuth.junit5;

import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.TestSleuthEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JUnitLifecycleEventCollectorTest {
    @TempDir
    private Path tempDir;

    @Test
    void recordsStartedAndFinishedTestEvents() {
        JUnitLifecycleEventCollector collector = new JUnitLifecycleEventCollector();
        TestIdentifier test = testIdentifier("test-id", "passes");

        collector.recordStarted(test);
        collector.recordFinished(test, TestExecutionResult.successful());

        List<TestSleuthEvent> events = collector.events();
        assertEquals(2, events.size());
        assertEquals(EventKind.TEST_STARTED, events.get(0).kind());
        assertEquals(EventKind.TEST_FINISHED, events.get(1).kind());
        assertEquals("dev.testsleuth.ExampleTest.passes", events.get(1).subject().identifier());
        assertEquals("dev.testsleuth.ExampleTest.passes", events.get(1).attributes().get("testIdentity"));
        assertEquals("passes", events.get(1).attributes().get("displayName"));
        assertEquals("passed", events.get(1).attributes().get("status"));
        assertTrue(Long.parseLong(events.get(1).attributes().get("durationMillis")) >= 0);
    }

    @Test
    void writesEventsToConfiguredFile() throws Exception {
        JUnitLifecycleEventCollector collector = new JUnitLifecycleEventCollector();
        TestIdentifier test = testIdentifier("test-id", "passes");
        Path eventsFile = tempDir.resolve("events/junit-events.json");

        collector.recordStarted(test);
        collector.recordFinished(test, TestExecutionResult.successful());
        collector.writeTo(eventsFile, message -> {
            throw new AssertionError(message);
        });

        String json = Files.readString(eventsFile);
        assertTrue(json.contains("\"collector\":\"junit5-listener\""));
        assertTrue(json.contains("\"kind\":\"TEST_FINISHED\""));
    }

    private static TestIdentifier testIdentifier(String uniqueId, String displayName) {
        return TestIdentifier.from(new TestDescriptorStub(
                uniqueId,
                displayName,
                MethodSource.from("dev.testsleuth.ExampleTest", displayName)
        ));
    }

    private static final class TestDescriptorStub extends AbstractTestDescriptor {
        private TestDescriptorStub(String uniqueId, String displayName, MethodSource methodSource) {
            super(UniqueId.forEngine("testsleuth").append("test", uniqueId), displayName, methodSource);
        }

        @Override
        public TestDescriptor.Type getType() {
            return TestDescriptor.Type.TEST;
        }
    }
}

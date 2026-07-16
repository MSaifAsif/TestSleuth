package dev.testsleuth.junit5;

import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.EventJsonReader;
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
    void recordsSetupAndTeardownPhaseEvents() throws Exception {
        JUnitLifecycleEventCollector collector = new JUnitLifecycleEventCollector();

        collector.recordPhaseStarted(
                EventKind.SETUP_STARTED,
                "before-each",
                "phase-id",
                "passes",
                java.util.Optional.of(ExampleTest.class),
                java.util.Optional.of(ExampleTest.class.getDeclaredMethod("passes"))
        );
        collector.recordPhaseFinished(
                EventKind.SETUP_FINISHED,
                "before-each",
                "phase-id",
                "passes",
                java.util.Optional.of(ExampleTest.class),
                java.util.Optional.of(ExampleTest.class.getDeclaredMethod("passes"))
        );
        collector.recordPhaseStarted(
                EventKind.TEARDOWN_STARTED,
                "after-each",
                "phase-id",
                "passes",
                java.util.Optional.of(ExampleTest.class),
                java.util.Optional.of(ExampleTest.class.getDeclaredMethod("passes"))
        );
        collector.recordPhaseFinished(
                EventKind.TEARDOWN_FINISHED,
                "after-each",
                "phase-id",
                "passes",
                java.util.Optional.of(ExampleTest.class),
                java.util.Optional.of(ExampleTest.class.getDeclaredMethod("passes"))
        );

        List<TestSleuthEvent> events = collector.events();
        assertEquals(EventKind.SETUP_STARTED, events.get(0).kind());
        assertEquals(EventKind.SETUP_FINISHED, events.get(1).kind());
        assertEquals(EventKind.TEARDOWN_STARTED, events.get(2).kind());
        assertEquals(EventKind.TEARDOWN_FINISHED, events.get(3).kind());
        assertEquals("before-each", events.get(1).attributes().get("phase"));
        assertEquals("after-each", events.get(3).attributes().get("phase"));
        assertEquals(
                JUnitLifecycleEventCollectorTest.ExampleTest.class.getName() + ".passes",
                events.get(1).attributes().get("testIdentity")
        );
        assertTrue(Long.parseLong(events.get(1).attributes().get("durationMillis")) >= 0);
        assertTrue(Long.parseLong(events.get(3).attributes().get("durationMillis")) >= 0);
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

    @Test
    void preservesExistingEventsWhenWritingToConfiguredFile() throws Exception {
        Path eventsFile = tempDir.resolve("events/junit-events.json");
        JUnitLifecycleEventCollector first = new JUnitLifecycleEventCollector();
        first.recordStarted(testIdentifier("first-id", "firstTest"));
        first.recordFinished(testIdentifier("first-id", "firstTest"), TestExecutionResult.successful());
        first.writeTo(eventsFile, message -> {
            throw new AssertionError(message);
        });

        JUnitLifecycleEventCollector second = new JUnitLifecycleEventCollector();
        second.recordStarted(testIdentifier("second-id", "secondTest"));
        second.recordFinished(testIdentifier("second-id", "secondTest"), TestExecutionResult.successful());
        second.writeTo(eventsFile, message -> {
            throw new AssertionError(message);
        });

        List<TestSleuthEvent> events = new EventJsonReader().read(Files.readString(eventsFile));
        assertEquals(4, events.size());
        assertEquals("dev.testsleuth.ExampleTest.firstTest", events.get(1).attributes().get("testIdentity"));
        assertEquals("dev.testsleuth.ExampleTest.secondTest", events.get(3).attributes().get("testIdentity"));
    }

    private static TestIdentifier testIdentifier(String uniqueId, String displayName) {
        return TestIdentifier.from(new TestDescriptorStub(
                uniqueId,
                displayName,
                MethodSource.from("dev.testsleuth.ExampleTest", displayName)
        ));
    }

    private static final class ExampleTest {
        @SuppressWarnings("unused")
        void passes() {
        }
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

package dev.testsleuth.maven;

import dev.testsleuth.core.event.EventId;
import dev.testsleuth.core.event.EventJsonReader;
import dev.testsleuth.core.event.EventJsonWriter;
import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.Subject;
import dev.testsleuth.core.event.SubjectType;
import dev.testsleuth.core.event.TestSleuthEvent;
import dev.testsleuth.core.event.TestSleuthRunContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MavenAggregateEventCollectorTest {
    @TempDir
    private Path tempDir;

    @Test
    void aggregatesModuleEventsFallbackXmlReportsAndNoTestModules() throws Exception {
        Path moduleWithEvents = module("module-with-events");
        Path fallbackModule = module("fallback-module");
        Path noTestModule = module("no-test-module");
        writeEvents(moduleWithEvents.resolve("target/testsleuth/events.json"), event(
                "module-events-test",
                "dev.testsleuth.ModuleEventsTest.slowTest",
                "junit5-listener",
                "1200"
        ));
        Path fallbackReports = fallbackModule.resolve("target/surefire-reports");
        Files.createDirectories(fallbackReports);
        Files.writeString(fallbackReports.resolve("TEST-dev.testsleuth.FallbackTest.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite tests="1" failures="0" errors="0" skipped="0">
                  <testcase classname="dev.testsleuth.FallbackTest" name="fromXml" time="1.500"/>
                </testsuite>
                """);

        MavenAggregateEventCollector.AggregateEvents result = new MavenAggregateEventCollector().collect(List.of(
                input(moduleWithEvents, List.of(reportDirectory(moduleWithEvents.resolve("target/surefire-reports")))),
                input(fallbackModule, List.of(reportDirectory(fallbackReports))),
                input(noTestModule, List.of(reportDirectory(noTestModule.resolve("target/surefire-reports"))))
        ));

        List<TestSleuthEvent> merged = new EventJsonReader().read(result.mergedEvents().json());
        assertEquals(2, merged.size());
        assertEquals(1, result.moduleEventFiles().size());
        assertEquals(1, result.scannedEvents().size());
        assertEquals(1, result.fallbackEvents().size());
        assertEquals(2, result.detectorEvents().size());
        assertTrue(merged.stream()
                .anyMatch(event -> "dev.testsleuth.ModuleEventsTest.slowTest".equals(event.subject().identifier())));
        assertTrue(merged.stream()
                .anyMatch(event -> "dev.testsleuth.FallbackTest.fromXml".equals(event.subject().identifier())));
    }

    @Test
    void fallsBackToJunitLifecycleFilesWhenModuleEventsFileIsMissing() throws Exception {
        Path module = module("junit-only-module");
        writeEvents(module.resolve("target/testsleuth/junit-events.json"), event(
                "junit-only-test",
                "dev.testsleuth.JUnitOnlyTest.observed",
                "junit5-listener",
                "250"
        ));

        MavenAggregateEventCollector.AggregateEvents result = new MavenAggregateEventCollector().collect(List.of(
                input(module, List.of(reportDirectory(module.resolve("target/surefire-reports"))))
        ));

        List<TestSleuthEvent> merged = new EventJsonReader().read(result.mergedEvents().json());
        assertEquals(1, merged.size());
        assertEquals(1, result.moduleEventFiles().size());
        assertEquals(1, result.detectorEvents().size());
        assertEquals("junit5-listener", merged.get(0).attributes().get("collector"));
    }

    private Path module(String name) throws Exception {
        Path module = tempDir.resolve(name);
        Files.createDirectories(module.resolve("target/testsleuth"));
        return module;
    }

    private MavenAggregateEventCollector.ModuleInput input(
            Path module,
            List<MavenTestReportScanner.ReportDirectory> reportDirectories
    ) {
        return new MavenAggregateEventCollector.ModuleInput(
                module.resolve("target/testsleuth"),
                reportDirectories,
                runContext(module.getFileName().toString())
        );
    }

    private static MavenTestReportScanner.ReportDirectory reportDirectory(Path path) {
        return new MavenTestReportScanner.ReportDirectory(path, Map.of("testRunner", "surefire"));
    }

    private static void writeEvents(Path path, TestSleuthEvent event) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, new EventJsonWriter().write(List.of(event)));
    }

    private static TestSleuthEvent event(String id, String testIdentity, String collector, String durationMillis) {
        return new TestSleuthEvent(
                new EventId(id),
                Optional.empty(),
                EventKind.TEST_FINISHED,
                new Subject(SubjectType.TEST_METHOD, testIdentity),
                Instant.EPOCH,
                1,
                Map.of(
                        "collector", collector,
                        "testIdentity", testIdentity,
                        "durationMillis", durationMillis,
                        "status", "passed"
                )
        );
    }

    private static TestSleuthRunContext runContext(String artifactId) {
        return new TestSleuthRunContext(
                "run-1",
                "dev.testsleuth:" + artifactId,
                "dev.testsleuth",
                artifactId,
                "0.1.0-SNAPSHOT",
                "/workspace/" + artifactId,
                "123",
                "unknown"
        );
    }
}

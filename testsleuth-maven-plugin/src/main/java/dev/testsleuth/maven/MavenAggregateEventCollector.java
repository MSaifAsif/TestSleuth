package dev.testsleuth.maven;

import dev.testsleuth.core.event.TestSleuthEvent;
import dev.testsleuth.core.event.TestSleuthRunContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class MavenAggregateEventCollector {
    AggregateEvents collect(List<ModuleInput> modules) {
        Objects.requireNonNull(modules, "modules");

        List<Path> moduleEventFiles = new ArrayList<>();
        List<TestSleuthEvent> scannedEvents = new ArrayList<>();
        List<TestSleuthEvent> fallbackEvents = new ArrayList<>();
        List<TestSleuthEvent> detectorEvents = new ArrayList<>();
        List<Duration> moduleLifecycleWindows = new ArrayList<>();
        TestSleuthEventJsonMerger merger = new TestSleuthEventJsonMerger();

        for (ModuleInput module : modules) {
            Path moduleEvents = module.outputDirectory().resolve("events.json");
            Path moduleJunit5Events = module.outputDirectory().resolve("junit-events.json");
            Path moduleJunit4Events = module.outputDirectory().resolve("junit4-events.json");
            Path moduleRuntimeWaitEvents = module.outputDirectory().resolve("runtime-wait-events.json");

            if (Files.isRegularFile(moduleEvents)) {
                moduleEventFiles.add(moduleEvents);
                detectorEvents.addAll(merger.readEvents(moduleEvents));
            } else {
                if (Files.isRegularFile(moduleJunit5Events)) {
                    moduleEventFiles.add(moduleJunit5Events);
                    detectorEvents.addAll(merger.readEvents(moduleJunit5Events));
                }
                if (Files.isRegularFile(moduleJunit4Events)) {
                    moduleEventFiles.add(moduleJunit4Events);
                    detectorEvents.addAll(merger.readEvents(moduleJunit4Events));
                }
                if (Files.isRegularFile(moduleRuntimeWaitEvents)) {
                    moduleEventFiles.add(moduleRuntimeWaitEvents);
                    detectorEvents.addAll(merger.readEvents(moduleRuntimeWaitEvents));
                }
            }

            new MavenBuildTiming().load(module.outputDirectory())
                    .map(MavenBuildTiming.RunTiming::elapsedSinceStart)
                    .ifPresent(moduleLifecycleWindows::add);

            MavenTestReportScanner.ScanResult moduleScanResult = new MavenTestReportScanner(module.runContext())
                    .scanReportDirectories(module.reportDirectories());
            scannedEvents.addAll(moduleScanResult.events());
            if (!Files.isRegularFile(moduleEvents)) {
                fallbackEvents.addAll(moduleScanResult.events());
                detectorEvents.addAll(moduleScanResult.events());
            }
        }

        TestSleuthEventJsonMerger.EventJson mergedEvents = merger.mergeEventFiles(moduleEventFiles, fallbackEvents);
        return new AggregateEvents(
                moduleEventFiles,
                scannedEvents,
                fallbackEvents,
                detectorEvents,
                moduleLifecycleWindows,
                mergedEvents
        );
    }

    record ModuleInput(
            Path outputDirectory,
            List<MavenTestReportScanner.ReportDirectory> reportDirectories,
            TestSleuthRunContext runContext
    ) {
        ModuleInput {
            Objects.requireNonNull(outputDirectory, "outputDirectory");
            reportDirectories = List.copyOf(Objects.requireNonNull(reportDirectories, "reportDirectories"));
            Objects.requireNonNull(runContext, "runContext");
        }
    }

    record AggregateEvents(
            List<Path> moduleEventFiles,
            List<TestSleuthEvent> scannedEvents,
            List<TestSleuthEvent> fallbackEvents,
            List<TestSleuthEvent> detectorEvents,
            List<Duration> moduleLifecycleWindows,
            TestSleuthEventJsonMerger.EventJson mergedEvents
    ) {
        AggregateEvents {
            moduleEventFiles = List.copyOf(Objects.requireNonNull(moduleEventFiles, "moduleEventFiles"));
            scannedEvents = List.copyOf(Objects.requireNonNull(scannedEvents, "scannedEvents"));
            fallbackEvents = List.copyOf(Objects.requireNonNull(fallbackEvents, "fallbackEvents"));
            detectorEvents = List.copyOf(Objects.requireNonNull(detectorEvents, "detectorEvents"));
            moduleLifecycleWindows = List.copyOf(Objects.requireNonNull(moduleLifecycleWindows, "moduleLifecycleWindows"));
            Objects.requireNonNull(mergedEvents, "mergedEvents");
        }
    }
}

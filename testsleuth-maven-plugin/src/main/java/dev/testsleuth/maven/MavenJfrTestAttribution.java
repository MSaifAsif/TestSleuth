package dev.testsleuth.maven;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MavenJfrTestAttribution {
    private static final String TEST_LIFECYCLE_EVENT = "dev.testsleuth.TestLifecycle";
    private static final Map<String, String> DIRECT_EVENT_FAMILIES = Map.of(
            "jdk.ThreadSleep", "ThreadSleep",
            "jdk.ThreadPark", "ThreadPark",
            "jdk.JavaMonitorEnter", "MonitorEnter",
            "jdk.SocketRead", "SocketRead",
            "jdk.SocketWrite", "SocketWrite",
            "jdk.FileRead", "FileRead",
            "jdk.FileWrite", "FileWrite",
            "jdk.ClassLoad", "ClassLoad"
    );
    private static final Map<String, String> SHARED_JVM_EVENT_FAMILIES = Map.of(
            "jdk.GarbageCollection", "GarbageCollection"
    );
    private static final Map<String, String> SAMPLED_EVENT_FAMILIES = Map.of(
            "jdk.ExecutionSample", "ExecutionSample",
            "jdk.ObjectAllocationSample", "ObjectAllocationSample"
    );

    Summary summarize(Path recordingsDirectory, boolean enabled) {
        if (!enabled || !Files.isDirectory(recordingsDirectory)) {
            return Summary.empty();
        }

        List<TestWindow> testWindows = new ArrayList<>();
        List<PhaseWindow> phaseWindows = new ArrayList<>();
        List<RuntimeEvent> runtimeEvents = new ArrayList<>();
        List<SampleEvent> sampleEvents = new ArrayList<>();
        List<SharedRuntimeEvent> sharedRuntimeEvents = new ArrayList<>();
        try (var paths = Files.list(recordingsDirectory)) {
            for (Path recording : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jfr"))
                    .sorted()
                    .toList()) {
                readRecording(recording, testWindows, phaseWindows, runtimeEvents, sampleEvents, sharedRuntimeEvents);
            }
        } catch (IOException ignored) {
            return Summary.empty();
        }

        Map<String, MutableEvidence> evidenceByTest = new LinkedHashMap<>();
        Map<PhaseIdentity, MutablePhaseEvidence> evidenceByPhase = new LinkedHashMap<>();
        Map<String, MutableSampleEvidence> samplesByTest = new LinkedHashMap<>();
        Map<PhaseIdentity, MutablePhaseSampleEvidence> samplesByPhase = new LinkedHashMap<>();
        Map<UnassignedScope, MutableUnassignedEvidence> unassignedEvidence = new LinkedHashMap<>();
        for (RuntimeEvent runtimeEvent : runtimeEvents) {
            PhaseWindow phaseWindow = containingPhaseWindow(phaseWindows, runtimeEvent);
            if (phaseWindow != null) {
                evidenceByPhase.computeIfAbsent(
                                new PhaseIdentity(phaseWindow.testIdentity, phaseWindow.phase),
                                ignored -> new MutablePhaseEvidence(phaseWindow.testIdentity, phaseWindow.phase)
                        )
                        .add(runtimeEvent);
                continue;
            }
            boolean matchedTest = false;
            for (TestWindow window : testWindows) {
                if (belongsToWindow(window.recording, window.threadId, window.start, window.end,
                        runtimeEvent.recording, runtimeEvent.threadId, runtimeEvent.start)) {
                    evidenceByTest.computeIfAbsent(window.testIdentity, ignored -> new MutableEvidence(window.testIdentity))
                            .add(runtimeEvent);
                    matchedTest = true;
                    break;
                }
            }
            if (!matchedTest) {
                UnassignedScope scope = unassignedScope(testWindows, runtimeEvent);
                unassignedEvidence.computeIfAbsent(scope, MutableUnassignedEvidence::new).add(runtimeEvent);
            }
        }
        for (SampleEvent sampleEvent : sampleEvents) {
            PhaseWindow phaseWindow = containingPhaseWindow(phaseWindows, sampleEvent);
            if (phaseWindow != null) {
                samplesByPhase.computeIfAbsent(
                                new PhaseIdentity(phaseWindow.testIdentity, phaseWindow.phase),
                                ignored -> new MutablePhaseSampleEvidence(phaseWindow.testIdentity, phaseWindow.phase)
                        )
                        .add(sampleEvent);
                continue;
            }
            for (TestWindow window : testWindows) {
                if (belongsToWindow(window.recording, window.threadId, window.start, window.end,
                        sampleEvent.recording, sampleEvent.threadId, sampleEvent.start)) {
                    samplesByTest.computeIfAbsent(window.testIdentity, MutableSampleEvidence::new).add(sampleEvent);
                    break;
                }
            }
        }
        for (SharedRuntimeEvent event : sharedRuntimeEvents) {
            UnassignedScope scope = sharedJvmScope(testWindows, event);
            unassignedEvidence.computeIfAbsent(scope, MutableUnassignedEvidence::new).add(event.family, event.duration);
        }
        List<TestEvidence> evidence = evidenceByTest.values().stream()
                .map(MutableEvidence::toEvidence)
                .sorted(Comparator.comparing(TestEvidence::directDuration).reversed())
                .toList();
        List<PhaseEvidence> phaseEvidence = evidenceByPhase.values().stream()
                .map(MutablePhaseEvidence::toEvidence)
                .sorted(Comparator.comparing(PhaseEvidence::directDuration).reversed())
                .toList();
        List<UnassignedEvidence> unassigned = unassignedEvidence.values().stream()
                .map(MutableUnassignedEvidence::toEvidence)
                .toList();
        List<SampleEvidence> samples = samplesByTest.values().stream()
                .map(MutableSampleEvidence::toEvidence)
                .sorted(Comparator.comparing(SampleEvidence::sampleCount).reversed())
                .toList();
        List<PhaseSampleEvidence> phaseSamples = samplesByPhase.values().stream()
                .map(MutablePhaseSampleEvidence::toEvidence)
                .sorted(Comparator.comparing(PhaseSampleEvidence::sampleCount).reversed())
                .toList();
        return new Summary(testWindows.size(), runtimeEvents.size(), sampleEvents.size(), evidence, phaseEvidence, samples, phaseSamples, unassigned);
    }

    private static UnassignedScope unassignedScope(List<TestWindow> windows, RuntimeEvent runtimeEvent) {
        Set<String> activeTests = new java.util.LinkedHashSet<>();
        for (TestWindow window : windows) {
            if (window.recording.equals(runtimeEvent.recording) && window.contains(runtimeEvent.start)) {
                activeTests.add(window.testIdentity);
            }
        }
        return unassignedScopeForActiveTests(activeTests.size());
    }

    static UnassignedScope unassignedScopeForActiveTests(int activeTestCount) {
        if (activeTestCount < 0) {
            throw new IllegalArgumentException("activeTestCount must not be negative");
        }
        if (activeTestCount == 0) {
            return UnassignedScope.UNCLASSIFIED;
        }
        if (activeTestCount == 1) {
            return UnassignedScope.CORRELATED_ASYNCHRONOUS;
        }
        return UnassignedScope.SHARED_CONCURRENT;
    }

    static UnassignedScope sharedJvmScopeForActiveTests(int activeTestCount) {
        if (activeTestCount < 0) {
            throw new IllegalArgumentException("activeTestCount must not be negative");
        }
        return activeTestCount == 0 ? UnassignedScope.UNCLASSIFIED : UnassignedScope.SHARED_JVM;
    }

    private static UnassignedScope sharedJvmScope(List<TestWindow> windows, SharedRuntimeEvent event) {
        int activeTests = 0;
        for (TestWindow window : windows) {
            if (window.recording.equals(event.recording) && windowsOverlap(window.start, window.end, event.start, event.end())) {
                activeTests++;
            }
        }
        return sharedJvmScopeForActiveTests(activeTests);
    }

    private static boolean windowsOverlap(Instant firstStart, Instant firstEnd, Instant secondStart, Instant secondEnd) {
        return !firstEnd.isBefore(secondStart) && !secondEnd.isBefore(firstStart);
    }

    private static PhaseWindow containingPhaseWindow(List<PhaseWindow> windows, RuntimeEvent runtimeEvent) {
        for (PhaseWindow window : windows) {
            if (belongsToWindow(window.recording, window.threadId, window.start, window.end,
                    runtimeEvent.recording, runtimeEvent.threadId, runtimeEvent.start)) {
                return window;
            }
        }
        return null;
    }

    private static PhaseWindow containingPhaseWindow(List<PhaseWindow> windows, SampleEvent sampleEvent) {
        for (PhaseWindow window : windows) {
            if (belongsToWindow(window.recording, window.threadId, window.start, window.end,
                    sampleEvent.recording, sampleEvent.threadId, sampleEvent.start)) {
                return window;
            }
        }
        return null;
    }

    static boolean belongsToWindow(
            Path windowRecording,
            long windowThreadId,
            Instant windowStart,
            Instant windowEnd,
            Path runtimeRecording,
            long runtimeThreadId,
            Instant runtimeStart
    ) {
        return windowRecording.equals(runtimeRecording)
                && windowThreadId == runtimeThreadId
                && !runtimeStart.isBefore(windowStart)
                && !runtimeStart.isAfter(windowEnd);
    }

    private static void readRecording(
            Path recording,
            List<TestWindow> testWindows,
            List<PhaseWindow> phaseWindows,
            List<RuntimeEvent> runtimeEvents,
            List<SampleEvent> sampleEvents,
            List<SharedRuntimeEvent> sharedRuntimeEvents
    ) {
        try (RecordingFile file = new RecordingFile(recording)) {
            while (file.hasMoreEvents()) {
                try {
                    readEvent(file.readEvent(), recording, testWindows, phaseWindows, runtimeEvents, sampleEvents, sharedRuntimeEvents);
                } catch (RuntimeException ignored) {
                    // Individual JFR event schemas may vary by JDK; retain the rest of the recording.
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // The recording reader reports validity separately; attribution skips invalid files.
        }
    }

    private static void readEvent(
            RecordedEvent event,
            Path recording,
            List<TestWindow> testWindows,
            List<PhaseWindow> phaseWindows,
            List<RuntimeEvent> runtimeEvents,
            List<SampleEvent> sampleEvents,
            List<SharedRuntimeEvent> sharedRuntimeEvents
    ) {
        if (TEST_LIFECYCLE_EVENT.equals(event.getEventType().getName())) {
            lifecycleWindow(event).ifPresent(window -> {
                if (window instanceof TestWindow testWindow) {
                    testWindows.add(testWindow.withRecording(recording));
                } else if (window instanceof PhaseWindow phaseWindow) {
                    phaseWindows.add(phaseWindow.withRecording(recording));
                }
            });
            return;
        }
        String family = DIRECT_EVENT_FAMILIES.get(event.getEventType().getName());
        if (family != null) {
            runtimeEvent(event, family).map(runtimeEvent -> runtimeEvent.withRecording(recording))
                    .ifPresent(runtimeEvents::add);
            return;
        }
        String sampledFamily = SAMPLED_EVENT_FAMILIES.get(event.getEventType().getName());
        if (sampledFamily != null) {
            sampleEvent(event, sampledFamily).map(sampleEvent -> sampleEvent.withRecording(recording))
                    .ifPresent(sampleEvents::add);
            return;
        }
        String sharedFamily = SHARED_JVM_EVENT_FAMILIES.get(event.getEventType().getName());
        if (sharedFamily != null) {
            sharedRuntimeEvent(event, sharedFamily).map(sharedEvent -> sharedEvent.withRecording(recording))
                    .ifPresent(sharedRuntimeEvents::add);
        }
    }

    private static java.util.Optional<LifecycleWindow> lifecycleWindow(RecordedEvent event) {
        RecordedThread thread = event.getThread("eventThread");
        String identity = event.getString("testIdentity");
        String phase = event.getString("phase");
        if (thread == null || identity == null || identity.isBlank() || phase == null || phase.isBlank()) {
            return java.util.Optional.empty();
        }
        if ("test".equals(phase)) {
            return java.util.Optional.of(new TestWindow(identity, thread.getJavaThreadId(), event.getStartTime(), event.getEndTime()));
        }
        return java.util.Optional.of(new PhaseWindow(identity, phase, thread.getJavaThreadId(), event.getStartTime(), event.getEndTime()));
    }

    private static java.util.Optional<RuntimeEvent> runtimeEvent(RecordedEvent event, String family) {
        RecordedThread thread = event.getThread("eventThread");
        if (thread == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new RuntimeEvent(
                family,
                thread.getJavaThreadId(),
                event.getStartTime(),
                event.getDuration(),
                stackLocation(event)
        ));
    }

    private static java.util.Optional<SampleEvent> sampleEvent(RecordedEvent event, String family) {
        RecordedThread thread = event.getThread("eventThread");
        if (thread == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new SampleEvent(family, thread.getJavaThreadId(), event.getStartTime(), stackLocation(event)));
    }

    private static java.util.Optional<SharedRuntimeEvent> sharedRuntimeEvent(RecordedEvent event, String family) {
        Duration duration = "GarbageCollection".equals(family) ? garbageCollectionPauseDuration(event) : event.getDuration();
        return java.util.Optional.of(new SharedRuntimeEvent(family, event.getStartTime(), duration));
    }

    private static Duration garbageCollectionPauseDuration(RecordedEvent event) {
        try {
            return event.getDuration("sumOfPauses");
        } catch (IllegalArgumentException ignored) {
            // Older or vendor-specific recordings may not expose pause aggregation.
            return event.getDuration();
        }
    }

    private static String stackLocation(RecordedEvent event) {
        RecordedStackTrace stackTrace = event.getStackTrace();
        if (stackTrace == null) {
            return "";
        }
        for (RecordedFrame frame : stackTrace.getFrames()) {
            String className = frame.getMethod().getType().getName();
            if (!isUserCode(className)) {
                continue;
            }
            int lineNumber = frame.getLineNumber();
            return className + "." + frame.getMethod().getName()
                    + (lineNumber > 0 ? ":" + lineNumber : "");
        }
        return "";
    }

    private static boolean isUserCode(String className) {
        return !className.startsWith("java.")
                && !className.startsWith("javax.")
                && !className.startsWith("jdk.")
                && !className.startsWith("sun.")
                && !className.startsWith("org.junit.")
                && !className.startsWith("org.apache.maven.")
                && !className.startsWith("org.codehaus.");
    }

    private sealed interface LifecycleWindow permits TestWindow, PhaseWindow {
    }

    private record TestWindow(String testIdentity, long threadId, Instant start, Instant end, Path recording)
            implements LifecycleWindow {
        private TestWindow(String testIdentity, long threadId, Instant start, Instant end) {
            this(testIdentity, threadId, start, end, null);
        }

        private TestWindow withRecording(Path recording) {
            return new TestWindow(testIdentity, threadId, start, end, recording);
        }

        boolean contains(Instant timestamp) {
            return !timestamp.isBefore(start) && !timestamp.isAfter(end);
        }
    }

    private record PhaseWindow(String testIdentity, String phase, long threadId, Instant start, Instant end, Path recording)
            implements LifecycleWindow {
        private PhaseWindow(String testIdentity, String phase, long threadId, Instant start, Instant end) {
            this(testIdentity, phase, threadId, start, end, null);
        }

        private PhaseWindow withRecording(Path recording) {
            return new PhaseWindow(testIdentity, phase, threadId, start, end, recording);
        }

        boolean contains(Instant timestamp) {
            return !timestamp.isBefore(start) && !timestamp.isAfter(end);
        }
    }

    private record RuntimeEvent(
            String family,
            long threadId,
            Instant start,
            Duration duration,
            String stackLocation,
            Path recording
    ) {
        private RuntimeEvent(String family, long threadId, Instant start, Duration duration, String stackLocation) {
            this(family, threadId, start, duration, stackLocation, null);
        }

        private RuntimeEvent withRecording(Path recording) {
            return new RuntimeEvent(family, threadId, start, duration, stackLocation, recording);
        }
    }

    private record SampleEvent(String family, long threadId, Instant start, String stackLocation, Path recording) {
        private SampleEvent(String family, long threadId, Instant start, String stackLocation) {
            this(family, threadId, start, stackLocation, null);
        }

        private SampleEvent withRecording(Path recording) {
            return new SampleEvent(family, threadId, start, stackLocation, recording);
        }
    }

    private record SharedRuntimeEvent(String family, Instant start, Duration duration, Path recording) {
        private SharedRuntimeEvent(String family, Instant start, Duration duration) {
            this(family, start, duration, null);
        }

        private SharedRuntimeEvent withRecording(Path recording) {
            return new SharedRuntimeEvent(family, start, duration, recording);
        }

        private Instant end() {
            return start.plus(duration);
        }
    }

    record TestEvidence(
            String testIdentity,
            Map<String, Long> eventCounts,
            Map<String, Duration> eventDurations,
            Map<String, String> eventLocations,
            Duration directDuration
    ) {
        TestEvidence {
            eventCounts = Map.copyOf(eventCounts);
            eventDurations = Map.copyOf(eventDurations);
            eventLocations = Map.copyOf(eventLocations);
        }
    }

    record PhaseEvidence(
            String testIdentity,
            String phase,
            Map<String, Long> eventCounts,
            Map<String, Duration> eventDurations,
            Map<String, String> eventLocations,
            Duration directDuration
    ) {
        PhaseEvidence {
            eventCounts = Map.copyOf(eventCounts);
            eventDurations = Map.copyOf(eventDurations);
            eventLocations = Map.copyOf(eventLocations);
        }
    }

    record UnassignedEvidence(
            UnassignedScope scope,
            Map<String, Long> eventCounts,
            Map<String, Duration> eventDurations,
            Duration duration
    ) {
        UnassignedEvidence {
            eventCounts = Map.copyOf(eventCounts);
            eventDurations = Map.copyOf(eventDurations);
        }
    }

    record SampleEvidence(String testIdentity, Map<String, Long> sampleCounts, Map<String, String> sampleLocations) {
        SampleEvidence {
            sampleCounts = Map.copyOf(sampleCounts);
            sampleLocations = Map.copyOf(sampleLocations);
        }

        long sampleCount() {
            return sampleCounts.values().stream().mapToLong(Long::longValue).sum();
        }
    }

    record PhaseSampleEvidence(String testIdentity, String phase, Map<String, Long> sampleCounts, Map<String, String> sampleLocations) {
        PhaseSampleEvidence {
            sampleCounts = Map.copyOf(sampleCounts);
            sampleLocations = Map.copyOf(sampleLocations);
        }

        long sampleCount() {
            return sampleCounts.values().stream().mapToLong(Long::longValue).sum();
        }
    }

    record Summary(
            int testWindowCount,
            int runtimeEventCount,
            int sampleEventCount,
            List<TestEvidence> evidence,
            List<PhaseEvidence> phaseEvidence,
            List<SampleEvidence> samples,
            List<PhaseSampleEvidence> phaseSamples,
            List<UnassignedEvidence> unassignedEvidence
    ) {
        Summary {
            evidence = List.copyOf(evidence);
            phaseEvidence = List.copyOf(phaseEvidence);
            samples = List.copyOf(samples);
            phaseSamples = List.copyOf(phaseSamples);
            unassignedEvidence = List.copyOf(unassignedEvidence);
        }

        static Summary empty() {
            return new Summary(0, 0, 0, List.of(), List.of(), List.of(), List.of(), List.of());
        }

        List<String> consoleLines() {
            List<String> lines = new ArrayList<>();
            if (evidence.isEmpty()) {
                lines.add("[TestSleuth] JFR direct evidence: no same-thread runtime events matched test-body windows");
            } else {
                TestEvidence top = evidence.get(0);
                lines.add("[TestSleuth] JFR direct evidence: " + evidence.size() + " tests matched from "
                        + testWindowCount + " test windows and " + runtimeEventCount + " candidate events; top "
                        + top.testIdentity + " " + top.eventCounts + " (" + top.directDuration().toMillis() + " ms)");
            }
            if (!phaseEvidence.isEmpty()) {
                PhaseEvidence top = phaseEvidence.get(0);
                lines.add("[TestSleuth] JFR phase evidence: " + phaseEvidence.size()
                        + " setup/teardown phases matched; top " + top.testIdentity + " " + top.phase
                        + " " + top.eventCounts + " (" + top.directDuration().toMillis() + " ms)");
            }
            if (!samples.isEmpty() || !phaseSamples.isEmpty()) {
                String top = !samples.isEmpty()
                        ? samples.get(0).testIdentity + " " + samples.get(0).sampleCounts
                        : phaseSamples.get(0).testIdentity + " " + phaseSamples.get(0).phase + " " + phaseSamples.get(0).sampleCounts;
                lines.add("[TestSleuth] JFR sampled signals: " + sampleEventCount
                        + " candidate samples; top " + top + "; samples indicate activity, not elapsed time");
            }
            for (UnassignedEvidence unassigned : unassignedEvidence) {
                lines.add("[TestSleuth] JFR " + unassigned.scope.consoleLabel + " evidence: "
                        + unassigned.eventCounts + " (" + unassigned.duration.toMillis() + " ms); "
                        + (unassigned.scope == UnassignedScope.SHARED_JVM
                        ? "shared JVM work while tests were active, not charged to a test"
                        : "not charged to a test")
                        + garbageCollectionPauseDetail(unassigned));
            }
            return List.copyOf(lines);
        }

        private static String garbageCollectionPauseDetail(UnassignedEvidence evidence) {
            Duration pauseDuration = evidence.eventDurations.get("GarbageCollection");
            return pauseDuration == null ? "" : "; GarbageCollection pause time " + pauseDuration.toMillis() + " ms";
        }
    }

    enum UnassignedScope {
        CORRELATED_ASYNCHRONOUS("asynchronous"),
        SHARED_CONCURRENT("shared-concurrent"),
        SHARED_JVM("shared JVM"),
        UNCLASSIFIED("unclassified");

        private final String consoleLabel;

        UnassignedScope(String consoleLabel) {
            this.consoleLabel = consoleLabel;
        }
    }

    private record PhaseIdentity(String testIdentity, String phase) {
    }

    private static final class MutableEvidence {
        private final String testIdentity;
        private final Map<String, Long> eventCounts = new LinkedHashMap<>();
        private final Map<String, Duration> eventDurations = new LinkedHashMap<>();
        private final Map<String, String> eventLocations = new LinkedHashMap<>();
        private Duration directDuration = Duration.ZERO;

        private MutableEvidence(String testIdentity) {
            this.testIdentity = testIdentity;
        }

        private void add(RuntimeEvent event) {
            eventCounts.merge(event.family, 1L, Long::sum);
            eventDurations.merge(event.family, event.duration, Duration::plus);
            if (!event.stackLocation.isBlank()) {
                eventLocations.putIfAbsent(event.family, event.stackLocation);
            }
            directDuration = directDuration.plus(event.duration);
        }

        private TestEvidence toEvidence() {
            return new TestEvidence(testIdentity, eventCounts, eventDurations, eventLocations, directDuration);
        }
    }

    private static final class MutablePhaseEvidence {
        private final String testIdentity;
        private final String phase;
        private final Map<String, Long> eventCounts = new LinkedHashMap<>();
        private final Map<String, Duration> eventDurations = new LinkedHashMap<>();
        private final Map<String, String> eventLocations = new LinkedHashMap<>();
        private Duration directDuration = Duration.ZERO;

        private MutablePhaseEvidence(String testIdentity, String phase) {
            this.testIdentity = testIdentity;
            this.phase = phase;
        }

        private void add(RuntimeEvent event) {
            eventCounts.merge(event.family, 1L, Long::sum);
            eventDurations.merge(event.family, event.duration, Duration::plus);
            if (!event.stackLocation.isBlank()) {
                eventLocations.putIfAbsent(event.family, event.stackLocation);
            }
            directDuration = directDuration.plus(event.duration);
        }

        private PhaseEvidence toEvidence() {
            return new PhaseEvidence(testIdentity, phase, eventCounts, eventDurations, eventLocations, directDuration);
        }
    }

    private static final class MutableUnassignedEvidence {
        private final UnassignedScope scope;
        private final Map<String, Long> eventCounts = new LinkedHashMap<>();
        private final Map<String, Duration> eventDurations = new LinkedHashMap<>();
        private Duration duration = Duration.ZERO;

        private MutableUnassignedEvidence(UnassignedScope scope) {
            this.scope = scope;
        }

        private void add(RuntimeEvent event) {
            add(event.family, event.duration);
        }

        private void add(String family, Duration eventDuration) {
            eventCounts.merge(family, 1L, Long::sum);
            eventDurations.merge(family, eventDuration, Duration::plus);
            duration = duration.plus(eventDuration);
        }

        private UnassignedEvidence toEvidence() {
            return new UnassignedEvidence(scope, eventCounts, eventDurations, duration);
        }
    }

    private static final class MutableSampleEvidence {
        private final String testIdentity;
        private final Map<String, Long> sampleCounts = new LinkedHashMap<>();
        private final Map<String, String> sampleLocations = new LinkedHashMap<>();

        private MutableSampleEvidence(String testIdentity) {
            this.testIdentity = testIdentity;
        }

        private void add(SampleEvent event) {
            sampleCounts.merge(event.family, 1L, Long::sum);
            if (!event.stackLocation.isBlank()) {
                sampleLocations.putIfAbsent(event.family, event.stackLocation);
            }
        }

        private SampleEvidence toEvidence() {
            return new SampleEvidence(testIdentity, sampleCounts, sampleLocations);
        }
    }

    private static final class MutablePhaseSampleEvidence {
        private final String testIdentity;
        private final String phase;
        private final Map<String, Long> sampleCounts = new LinkedHashMap<>();
        private final Map<String, String> sampleLocations = new LinkedHashMap<>();

        private MutablePhaseSampleEvidence(String testIdentity, String phase) {
            this.testIdentity = testIdentity;
            this.phase = phase;
        }

        private void add(SampleEvent event) {
            sampleCounts.merge(event.family, 1L, Long::sum);
            if (!event.stackLocation.isBlank()) {
                sampleLocations.putIfAbsent(event.family, event.stackLocation);
            }
        }

        private PhaseSampleEvidence toEvidence() {
            return new PhaseSampleEvidence(testIdentity, phase, sampleCounts, sampleLocations);
        }
    }
}

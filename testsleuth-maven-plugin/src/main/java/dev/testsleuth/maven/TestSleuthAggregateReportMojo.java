package dev.testsleuth.maven;

import dev.testsleuth.core.event.TestSleuthEvent;
import dev.testsleuth.core.event.TestSleuthRunContext;
import dev.testsleuth.core.finding.Finding;
import dev.testsleuth.core.finding.FindingJsonWriter;
import dev.testsleuth.report.HtmlReportRenderer;
import dev.testsleuth.report.HtmlReportRenderer.ReportModel;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Mojo(name = "aggregate-report", aggregator = true, threadSafe = true)
public final class TestSleuthAggregateReportMojo extends AbstractMojo {
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(defaultValue = "${session.executionRootDirectory}/target/testsleuth", readonly = true)
    private String outputDirectory;

    @Parameter(property = "testsleuth.console.enabled", defaultValue = "true")
    private boolean consoleEnabled;

    @Parameter(property = "testsleuth.console.detail", defaultValue = "summary")
    private String consoleDetail;

    @Parameter(property = "testsleuth.threshold.slowTestMillis", defaultValue = "1000")
    private long slowTestMillis;

    @Parameter(property = "testsleuth.threshold.verySlowTestMillis", defaultValue = "5000")
    private long verySlowTestMillis;

    @Parameter(property = "testsleuth.threshold.fixedWaitMillis", defaultValue = "250")
    private long fixedWaitMillis;

    @Parameter(property = "testsleuth.threshold.pollingWaitMillis", defaultValue = "100")
    private long pollingWaitMillis;

    @Parameter(property = "testsleuth.findings.max", defaultValue = "10")
    private int maxFindings;

    @Parameter(property = "testsleuth.detectors.slowTests", defaultValue = "true")
    private boolean slowTestsDetectorEnabled;

    @Parameter(property = "testsleuth.detectors.fixedWaits", defaultValue = "false")
    private boolean fixedWaitsDetectorEnabled;

    @Parameter(property = "testsleuth.detectors.pollingWaits", defaultValue = "false")
    private boolean pollingWaitsDetectorEnabled;

    @Parameter(property = "testsleuth.detectors.frameworkInitialization", defaultValue = "false")
    private boolean frameworkInitializationDetectorEnabled;

    @Parameter(property = "testsleuth.runtime.waits", defaultValue = "false")
    private boolean runtimeWaitsEnabled;

    @Parameter(property = "testsleuth.runtime.waitStacks", defaultValue = "false")
    private boolean runtimeWaitStacksEnabled;

    @Override
    public void execute() throws MojoExecutionException {
        long reportStartedNanos = System.nanoTime();
        TestSleuthMavenConfig config = createConfig();
        Path output = Path.of(outputDirectory);
        Path report = output.resolve("index.html");
        Path events = output.resolve("events.json");
        Path findingsFile = output.resolve("findings.json");

        List<MavenAggregateEventCollector.ModuleInput> modules = new ArrayList<>();
        MavenRunContextFactory runContextFactory = new MavenRunContextFactory();
        TestSleuthEventJsonMerger merger = new TestSleuthEventJsonMerger();

        for (MavenProject reactorProject : session.getProjects()) {
            Path moduleOutput = moduleOutputDirectory(reactorProject);
            TestSleuthRunContext runContext = runContextFactory.loadOrCreate(
                    moduleOutput,
                    reactorProject,
                    session.getUserProperties()
            );
            modules.add(new MavenAggregateEventCollector.ModuleInput(
                    moduleOutput,
                    reportDirectories(reactorProject),
                    runContext
            ));
        }

        MavenAggregateEventCollector.AggregateEvents aggregateEvents = new MavenAggregateEventCollector().collect(modules);
        int junitLifecycleEvents = merger.countAttributeValue(aggregateEvents.mergedEvents().json(), "collector", "junit5-listener")
                + merger.countAttributeValue(aggregateEvents.mergedEvents().json(), "collector", "junit4-listener");
        MavenTestReportScanner.ScanResult scanResult = new MavenTestReportScanner.ScanResult(aggregateEvents.scannedEvents());
        MavenTimingSummary timingSummary = MavenTimingSummary.from(
                aggregateLifecycleWindow(aggregateEvents.moduleLifecycleWindows()),
                aggregateEvents.detectorEvents()
        );
        MavenRuntimeWaitSummary runtimeWaitSummary = MavenRuntimeWaitSummary.from(aggregateEvents.detectorEvents());
        List<Finding> findings = detectFindings(config, aggregateEvents.detectorEvents());
        java.time.Duration reportPreparationTime = elapsedSince(reportStartedNanos);

        ReportModel model = new ReportModel(
                "TestSleuth Aggregate Report",
                "Aggregated " + session.getProjects().size() + " Maven projects, "
                        + scanResult.testCount() + " Maven test results, and "
                        + junitLifecycleEvents + " JUnit lifecycle events. "
                        + aggregateLifecycleSummary(aggregateEvents.moduleLifecycleWindows())
                        + timingSummary.reportSentence()
                        + reportOverheadSummary(reportPreparationTime)
                        + "Showing findings above " + config.slowTestThreshold().toMillis() + " ms.",
                findings
        );

        try {
            Files.createDirectories(output);
            Files.writeString(events, aggregateEvents.mergedEvents().json(), StandardCharsets.UTF_8);
            Files.writeString(findingsFile, new FindingJsonWriter().write(findings), StandardCharsets.UTF_8);
            Files.writeString(report, new HtmlReportRenderer().render(model), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write aggregate TestSleuth report", e);
        }
        java.time.Duration reportGenerationTime = elapsedSince(reportStartedNanos);

        new ConsoleSummaryReporter().report(
                getLog(),
                config,
                scanResult,
                junitLifecycleEvents,
                timingSummary,
                runtimeWaitSummary,
                reportGenerationTime,
                findings,
                report,
                events,
                findingsFile
        );
    }

    private TestSleuthMavenConfig createConfig() throws MojoExecutionException {
        try {
            return TestSleuthMavenConfig.from(
                    consoleEnabled,
                    consoleDetail,
                    slowTestMillis,
                    verySlowTestMillis,
                    maxFindings,
                    slowTestsDetectorEnabled,
                    fixedWaitsDetectorEnabled,
                    fixedWaitMillis,
                    pollingWaitsDetectorEnabled,
                    pollingWaitMillis,
                    frameworkInitializationDetectorEnabled,
                    runtimeWaitsEnabled,
                    runtimeWaitStacksEnabled
            );
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException("Invalid TestSleuth configuration: " + e.getMessage(), e);
        }
    }

    private List<MavenTestReportScanner.ReportDirectory> reportDirectories(MavenProject reactorProject)
            throws MojoExecutionException {
        Path buildDirectory = buildDirectory(reactorProject);
        return new MavenTestRunnerContext().reportDirectories(reactorProject, buildDirectory);
    }

    private List<Finding> detectFindings(TestSleuthMavenConfig config, List<TestSleuthEvent> events)
            throws MojoExecutionException {
        List<Finding> findings = new ArrayList<>(new MavenTimingFindings(config).detect(events));
        findings.addAll(new MavenRuntimeWaitFindings(config).detect(events));
        MavenRunContextFactory runContextFactory = new MavenRunContextFactory();
        for (MavenProject reactorProject : session.getProjects()) {
            TestSleuthRunContext runContext = runContextFactory.loadOrCreate(
                    moduleOutputDirectory(reactorProject),
                    reactorProject,
                    session.getUserProperties()
            );
            findings.addAll(new MavenFixedWaitFindings(config, runContext)
                    .detect(reactorProject.getTestCompileSourceRoots()));
            findings.addAll(new MavenPollingWaitFindings(config, runContext)
                    .detect(reactorProject.getTestCompileSourceRoots()));
            findings.addAll(new MavenFrameworkInitializationFindings(config, runContext)
                    .detect(reactorProject.getTestCompileSourceRoots(), events));
        }
        return findings;
    }

    private static Path moduleOutputDirectory(MavenProject reactorProject) throws MojoExecutionException {
        return buildDirectory(reactorProject).resolve("testsleuth");
    }

    private static Path buildDirectory(MavenProject reactorProject) throws MojoExecutionException {
        Build build = reactorProject.getBuild();
        if (build == null || build.getDirectory() == null || build.getDirectory().isBlank()) {
            throw new MojoExecutionException("Unable to locate Maven build directory for "
                    + reactorProject.getGroupId() + ":" + reactorProject.getArtifactId());
        }
        return Path.of(build.getDirectory());
    }

    private static String aggregateLifecycleSummary(List<java.time.Duration> windows) {
        return aggregateLifecycleWindow(windows)
                .map(duration -> "Longest observed Maven lifecycle window " + duration.toMillis() + " ms. ")
                .orElse("");
    }

    private static java.util.Optional<java.time.Duration> aggregateLifecycleWindow(List<java.time.Duration> windows) {
        return windows.stream().max(java.util.Comparator.naturalOrder());
    }

    private static String reportOverheadSummary(java.time.Duration duration) {
        return "TestSleuth report preparation " + duration.toMillis() + " ms. ";
    }

    private static java.time.Duration elapsedSince(long startedNanos) {
        long elapsed = System.nanoTime() - startedNanos;
        if (elapsed < 0) {
            return java.time.Duration.ZERO;
        }
        return java.time.Duration.ofNanos(elapsed);
    }
}

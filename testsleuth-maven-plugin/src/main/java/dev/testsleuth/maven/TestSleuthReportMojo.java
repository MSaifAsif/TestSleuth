package dev.testsleuth.maven;

import dev.testsleuth.core.event.TestSleuthRunContext;
import dev.testsleuth.core.event.TestSleuthEvent;
import dev.testsleuth.core.finding.Finding;
import dev.testsleuth.core.finding.FindingJsonWriter;
import dev.testsleuth.report.HtmlReportRenderer;
import dev.testsleuth.report.HtmlReportRenderer.ReportModel;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Mojo(name = "report", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public final class TestSleuthReportMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project.build.directory}/testsleuth", readonly = true)
    private String outputDirectory;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

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
        Path junit5Events = output.resolve("junit-events.json");
        Path junit4Events = output.resolve("junit4-events.json");
        TestSleuthRunContext runContext = new MavenRunContextFactory()
                .loadOrCreate(output, project, session.getUserProperties());
        java.util.Optional<MavenBuildTiming.RunTiming> runTiming = new MavenBuildTiming().load(output);

        MavenTestReportScanner.ScanResult scanResult = scanTestReports(runContext);
        TestSleuthEventJsonMerger merger = new TestSleuthEventJsonMerger();
        List<Path> junitEventFiles = List.of(junit5Events, junit4Events);
        List<TestSleuthEvent> junitLifecycleEvents = junitEventFiles.stream()
                .flatMap(file -> merger.readEvents(file).stream())
                .toList();
        TestSleuthEventJsonMerger.EventJson mergedEvents = merger.mergeEventFiles(junitEventFiles, scanResult.events());
        List<TestSleuthEvent> detectorEvents = new java.util.ArrayList<>(junitLifecycleEvents);
        detectorEvents.addAll(scanResult.events());
        MavenTimingSummary timingSummary = MavenTimingSummary.from(
                runTiming.map(MavenBuildTiming.RunTiming::elapsedSinceStart),
                detectorEvents
        );
        List<Finding> findings = detectFindings(config, detectorEvents, runContext);
        java.time.Duration reportPreparationTime = elapsedSince(reportStartedNanos);

        ReportModel model = new ReportModel(
                "TestSleuth Report",
                "Observed " + scanResult.testCount() + " Maven test results and "
                        + junitLifecycleEvents.size() + " JUnit lifecycle events. "
                        + lifecycleSummary(runTiming)
                        + timingSummary.reportSentence()
                        + reportOverheadSummary(reportPreparationTime)
                        + "Showing findings above " + config.slowTestThreshold().toMillis() + " ms.",
                findings
        );

        try {
            Files.createDirectories(output);
            Files.writeString(events, mergedEvents.json(), StandardCharsets.UTF_8);
            Files.writeString(findingsFile, new FindingJsonWriter().write(findings), StandardCharsets.UTF_8);
            Files.writeString(report, new HtmlReportRenderer().render(model), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write TestSleuth report", e);
        }
        java.time.Duration reportGenerationTime = elapsedSince(reportStartedNanos);

        new ConsoleSummaryReporter().report(
                getLog(),
                config,
                scanResult,
                junitLifecycleEvents.size(),
                timingSummary,
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

    private MavenTestReportScanner.ScanResult scanTestReports(TestSleuthRunContext runContext) throws MojoExecutionException {
        Build build = project.getBuild();
        if (build == null || build.getDirectory() == null || build.getDirectory().isBlank()) {
            throw new MojoExecutionException("Unable to locate Maven build directory");
        }

        Path buildDirectory = Path.of(build.getDirectory());
        return new MavenTestReportScanner(runContext)
                .scanReportDirectories(new MavenTestRunnerContext().reportDirectories(project, buildDirectory));
    }

    private List<Finding> detectFindings(
            TestSleuthMavenConfig config,
            List<TestSleuthEvent> events,
            TestSleuthRunContext runContext
    ) {
        List<Finding> findings = new java.util.ArrayList<>(new MavenTimingFindings(config).detect(events));
        findings.addAll(new MavenFixedWaitFindings(config, runContext).detect(project.getTestCompileSourceRoots()));
        findings.addAll(new MavenPollingWaitFindings(config, runContext).detect(project.getTestCompileSourceRoots()));
        findings.addAll(new MavenFrameworkInitializationFindings(config, runContext)
                .detect(project.getTestCompileSourceRoots(), events));
        return findings;
    }

    private static String lifecycleSummary(java.util.Optional<MavenBuildTiming.RunTiming> runTiming) {
        return runTiming
                .map(timing -> "Observed Maven lifecycle window " + timing.elapsedSinceStart().toMillis() + " ms. ")
                .orElse("");
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

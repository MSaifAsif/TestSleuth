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

    @Parameter(property = "testsleuth.findings.max", defaultValue = "10")
    private int maxFindings;

    @Parameter(property = "testsleuth.detectors.slowTests", defaultValue = "true")
    private boolean slowTestsDetectorEnabled;

    @Parameter(property = "testsleuth.detectors.fixedWaits", defaultValue = "false")
    private boolean fixedWaitsDetectorEnabled;

    @Override
    public void execute() throws MojoExecutionException {
        TestSleuthMavenConfig config = createConfig();
        Path output = Path.of(outputDirectory);
        Path report = output.resolve("index.html");
        Path events = output.resolve("events.json");
        Path findingsFile = output.resolve("findings.json");

        List<Path> moduleEventFiles = new ArrayList<>();
        List<TestSleuthEvent> scannedEvents = new ArrayList<>();
        List<TestSleuthEvent> fallbackEvents = new ArrayList<>();
        List<TestSleuthEvent> detectorEvents = new ArrayList<>();
        MavenRunContextFactory runContextFactory = new MavenRunContextFactory();
        TestSleuthEventJsonMerger merger = new TestSleuthEventJsonMerger();

        for (MavenProject reactorProject : session.getProjects()) {
            Path moduleOutput = moduleOutputDirectory(reactorProject);
            Path moduleEvents = moduleOutput.resolve("events.json");
            MavenTestReportScanner.ScanResult moduleScanResult;
            if (Files.isRegularFile(moduleEvents)) {
                moduleEventFiles.add(moduleEvents);
                detectorEvents.addAll(merger.readEvents(moduleEvents));
            }

            TestSleuthRunContext runContext = runContextFactory.loadOrCreate(
                    moduleOutput,
                    reactorProject,
                    session.getUserProperties()
            );
            moduleScanResult = scanTestReports(reactorProject, runContext);
            scannedEvents.addAll(moduleScanResult.events());
            if (!Files.isRegularFile(moduleEvents)) {
                fallbackEvents.addAll(moduleScanResult.events());
                detectorEvents.addAll(moduleScanResult.events());
            }
        }

        TestSleuthEventJsonMerger.EventJson mergedEvents = merger.mergeEventFiles(moduleEventFiles, fallbackEvents);
        int junitLifecycleEvents = merger.countAttributeValue(mergedEvents.json(), "collector", "junit5-listener");
        MavenTestReportScanner.ScanResult scanResult = new MavenTestReportScanner.ScanResult(scannedEvents);
        List<Finding> findings = detectFindings(config, detectorEvents);

        ReportModel model = new ReportModel(
                "TestSleuth Aggregate Report",
                "Aggregated " + session.getProjects().size() + " Maven projects, "
                        + scanResult.testCount() + " Maven test results, and "
                        + junitLifecycleEvents + " JUnit lifecycle events. "
                        + "Showing findings above " + config.slowTestThreshold().toMillis() + " ms.",
                findings
        );

        try {
            Files.createDirectories(output);
            Files.writeString(events, mergedEvents.json(), StandardCharsets.UTF_8);
            Files.writeString(findingsFile, new FindingJsonWriter().write(findings), StandardCharsets.UTF_8);
            Files.writeString(report, new HtmlReportRenderer().render(model), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write aggregate TestSleuth report", e);
        }

        new ConsoleSummaryReporter().report(
                getLog(),
                config,
                scanResult,
                junitLifecycleEvents,
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
                    fixedWaitMillis
            );
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException("Invalid TestSleuth configuration: " + e.getMessage(), e);
        }
    }

    private MavenTestReportScanner.ScanResult scanTestReports(
            MavenProject reactorProject,
            TestSleuthRunContext runContext
    ) throws MojoExecutionException {
        Path buildDirectory = buildDirectory(reactorProject);
        return new MavenTestReportScanner(runContext)
                .scanReportDirectories(new MavenTestRunnerContext().reportDirectories(reactorProject, buildDirectory));
    }

    private List<Finding> detectFindings(TestSleuthMavenConfig config, List<TestSleuthEvent> events)
            throws MojoExecutionException {
        List<Finding> findings = new ArrayList<>(new MavenTimingFindings(config).detect(events));
        MavenRunContextFactory runContextFactory = new MavenRunContextFactory();
        for (MavenProject reactorProject : session.getProjects()) {
            TestSleuthRunContext runContext = runContextFactory.loadOrCreate(
                    moduleOutputDirectory(reactorProject),
                    reactorProject,
                    session.getUserProperties()
            );
            findings.addAll(new MavenFixedWaitFindings(config, runContext)
                    .detect(reactorProject.getTestCompileSourceRoots()));
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
}

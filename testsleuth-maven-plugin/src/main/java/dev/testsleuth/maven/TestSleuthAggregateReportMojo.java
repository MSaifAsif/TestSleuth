package dev.testsleuth.maven;

import dev.testsleuth.core.event.TestSleuthEvent;
import dev.testsleuth.core.event.TestSleuthRunContext;
import dev.testsleuth.core.finding.Finding;
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

    @Parameter(property = "testsleuth.findings.max", defaultValue = "10")
    private int maxFindings;

    @Parameter(property = "testsleuth.detectors.slowTests", defaultValue = "true")
    private boolean slowTestsDetectorEnabled;

    @Override
    public void execute() throws MojoExecutionException {
        TestSleuthMavenConfig config = createConfig();
        Path output = Path.of(outputDirectory);
        Path report = output.resolve("index.html");
        Path events = output.resolve("events.json");

        List<Path> moduleEventFiles = new ArrayList<>();
        List<TestSleuthEvent> scannedEvents = new ArrayList<>();
        MavenRunContextFactory runContextFactory = new MavenRunContextFactory();

        for (MavenProject reactorProject : session.getProjects()) {
            Path moduleOutput = moduleOutputDirectory(reactorProject);
            Path moduleEvents = moduleOutput.resolve("events.json");
            if (Files.isRegularFile(moduleEvents)) {
                moduleEventFiles.add(moduleEvents);
            }

            TestSleuthRunContext runContext = runContextFactory.loadOrCreate(
                    moduleOutput,
                    reactorProject,
                    session.getUserProperties()
            );
            scannedEvents.addAll(scanTestReports(reactorProject, runContext).events());
        }

        TestSleuthEventJsonMerger merger = new TestSleuthEventJsonMerger();
        TestSleuthEventJsonMerger.EventJson mergedEvents = merger.mergeEventFiles(moduleEventFiles);
        int junitLifecycleEvents = merger.countAttributeValue(mergedEvents.json(), "collector", "junit5-listener");
        MavenTestReportScanner.ScanResult scanResult = new MavenTestReportScanner.ScanResult(scannedEvents);
        List<Finding> findings = new MavenTimingFindings(config).detect(scannedEvents);

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
                events
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
                    slowTestsDetectorEnabled
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
        return new MavenTestReportScanner(runContext).scan(List.of(
                buildDirectory.resolve("surefire-reports"),
                buildDirectory.resolve("failsafe-reports")
        ));
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

package dev.testsleuth.maven;

import dev.testsleuth.core.event.TestSleuthRunContext;
import dev.testsleuth.core.event.TestSleuthEvent;
import dev.testsleuth.core.finding.Finding;
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
        Path junitEvents = output.resolve("junit-events.json");
        TestSleuthRunContext runContext = new MavenRunContextFactory()
                .loadOrCreate(output, project, session.getUserProperties());

        MavenTestReportScanner.ScanResult scanResult = scanTestReports(runContext);
        TestSleuthEventJsonMerger.EventJson mergedEvents = new TestSleuthEventJsonMerger()
                .merge(junitEvents, scanResult.events());
        List<Finding> findings = new MavenTimingFindings(config).detect(scanResult.events());

        ReportModel model = new ReportModel(
                "TestSleuth Report",
                "Observed " + scanResult.testCount() + " Maven test results and "
                        + mergedEvents.preexistingEventCount() + " JUnit lifecycle events. "
                        + "Showing findings above " + config.slowTestThreshold().toMillis() + " ms.",
                findings
        );

        try {
            Files.createDirectories(output);
            Files.writeString(events, mergedEvents.json(), StandardCharsets.UTF_8);
            Files.writeString(report, new HtmlReportRenderer().render(model), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write TestSleuth report", e);
        }

        new ConsoleSummaryReporter().report(
                getLog(),
                config,
                scanResult,
                mergedEvents.preexistingEventCount(),
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

    private MavenTestReportScanner.ScanResult scanTestReports(TestSleuthRunContext runContext) throws MojoExecutionException {
        Build build = project.getBuild();
        if (build == null || build.getDirectory() == null || build.getDirectory().isBlank()) {
            throw new MojoExecutionException("Unable to locate Maven build directory");
        }

        Path buildDirectory = Path.of(build.getDirectory());
        return new MavenTestReportScanner(runContext).scan(List.of(
                buildDirectory.resolve("surefire-reports"),
                buildDirectory.resolve("failsafe-reports")
        ));
    }
}

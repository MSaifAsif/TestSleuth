package dev.testsleuth.maven;

import dev.testsleuth.report.HtmlReportRenderer;
import dev.testsleuth.report.HtmlReportRenderer.ReportModel;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
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

    @Override
    public void execute() throws MojoExecutionException {
        Path output = Path.of(outputDirectory);
        Path report = output.resolve("index.html");

        ReportModel model = new ReportModel(
                "TestSleuth Report",
                "Measurement collectors are not implemented yet. This shell report verifies plugin wiring.",
                List.of()
        );

        try {
            Files.createDirectories(output);
            Files.writeString(report, new HtmlReportRenderer().render(model), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write TestSleuth report", e);
        }

        getLog().info("Wrote TestSleuth report to " + report);
    }
}


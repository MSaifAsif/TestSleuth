package dev.testsleuth.maven;

import dev.testsleuth.core.event.TestSleuthRunContext;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;

@Mojo(name = "instrument", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true)
public final class TestSleuthInstrumentMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(property = "testsleuth.version")
    private String testSleuthVersion;

    @Override
    public void execute() throws MojoExecutionException {
        Build build = project.getBuild();
        if (build == null || build.getDirectory() == null || build.getDirectory().isBlank()) {
            throw new MojoExecutionException("Unable to locate Maven build directory");
        }

        Path eventsFile = Path.of(build.getDirectory()).resolve("testsleuth").resolve("junit-events.json");
        Path outputDirectory = eventsFile.getParent();
        MavenRunContextFactory runContextFactory = new MavenRunContextFactory();
        TestSleuthRunContext runContext = runContextFactory.create(project, session.getUserProperties());
        String instrumentationVersion = configuredVersion();
        MavenTestInstrumentation.Result result = new MavenTestInstrumentation().apply(
                project,
                session.getUserProperties(),
                eventsFile,
                instrumentationVersion,
                runContext
        );
        runContextFactory.write(outputDirectory, runContext);
        new MavenBuildTiming().start(outputDirectory);

        if (result.dependencyAdded()) {
            getLog().info("Added testsleuth-junit5 to the test runtime classpath");
        } else {
            getLog().debug("testsleuth-junit5 is already present in project dependencies");
        }
        getLog().info("Configured JUnit lifecycle event output at " + result.eventsFile());
        getLog().debug("Configured TestSleuth build run " + runContext.buildRunId()
                + " for module " + runContext.moduleId());
    }

    private String configuredVersion() throws MojoExecutionException {
        if (testSleuthVersion != null && !testSleuthVersion.isBlank()) {
            return testSleuthVersion;
        }
        return TestSleuthPluginMetadata.load().version()
                .orElseThrow(() -> new MojoExecutionException("Unable to determine TestSleuth plugin version"));
    }
}

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

    @Parameter(property = "testsleuth.runtime.waits", defaultValue = "false")
    private boolean runtimeWaitsEnabled;

    @Parameter(property = "testsleuth.runtime.waitStacks", defaultValue = "false")
    private boolean runtimeWaitStacksEnabled;

    @Parameter(property = MavenJfrRecording.ENABLED_PROPERTY, defaultValue = "false")
    private boolean jfrEnabled;

    @Override
    public void execute() throws MojoExecutionException {
        Build build = project.getBuild();
        if (build == null || build.getDirectory() == null || build.getDirectory().isBlank()) {
            throw new MojoExecutionException("Unable to locate Maven build directory");
        }

        Path outputDirectory = Path.of(build.getDirectory()).resolve("testsleuth");
        Path junit5EventsFile = outputDirectory.resolve("junit-events.json");
        Path junit4EventsFile = outputDirectory.resolve("junit4-events.json");
        Path runtimeWaitEventsFile = outputDirectory.resolve("runtime-wait-events.json");
        new MavenTestSleuthRunFiles().reset(outputDirectory);
        boolean effectiveJfrEnabled = prepareJfrRecordings(outputDirectory);
        MavenRunContextFactory runContextFactory = new MavenRunContextFactory();
        TestSleuthRunContext runContext = runContextFactory.create(project, session.getUserProperties());
        String instrumentationVersion = configuredVersion();
        MavenTestInstrumentation.Result result = new MavenTestInstrumentation().apply(
                project,
                session.getUserProperties(),
                junit5EventsFile,
                junit4EventsFile,
                runtimeWaitEventsFile,
                instrumentationVersion,
                runContext,
                runtimeWaitsEnabled,
                runtimeWaitStacksEnabled,
                effectiveJfrEnabled,
                new MavenJfrRecording().recordingsDirectory(outputDirectory)
        );
        runContextFactory.write(outputDirectory, runContext);
        new MavenBuildTiming().start(outputDirectory);

        if (result.dependencyAdded()) {
            getLog().info("Added TestSleuth JUnit listener modules to the test runtime classpath");
        } else {
            getLog().debug("TestSleuth JUnit listener modules are already present in project dependencies");
        }
        getLog().info("Configured JUnit 5 lifecycle event output at " + result.junit5EventsFile());
        getLog().info("Configured JUnit 4 lifecycle event output at " + result.junit4EventsFile());
        if (result.runtimeWaitsEnabled()) {
            getLog().info("Configured runtime wait event output at " + result.runtimeWaitEventsFile());
        }
        if (result.jfrEnabled()) {
            getLog().info("Configured bounded JFR recordings at "
                    + new MavenJfrRecording().recordingsDirectory(outputDirectory));
        }
        getLog().debug("Configured TestSleuth build run " + runContext.buildRunId()
                + " for module " + runContext.moduleId());
    }

    private boolean prepareJfrRecordings(Path outputDirectory) {
        if (!jfrEnabled) {
            return false;
        }
        try {
            new MavenJfrRecording().reset(outputDirectory);
            return true;
        } catch (IllegalStateException e) {
            getLog().warn("TestSleuth could not prepare JFR recording output; continuing without JFR: " + e.getMessage());
            return false;
        }
    }

    private String configuredVersion() throws MojoExecutionException {
        if (testSleuthVersion != null && !testSleuthVersion.isBlank()) {
            return testSleuthVersion;
        }
        return TestSleuthPluginMetadata.load().version()
                .orElseThrow(() -> new MojoExecutionException("Unable to determine TestSleuth plugin version"));
    }
}

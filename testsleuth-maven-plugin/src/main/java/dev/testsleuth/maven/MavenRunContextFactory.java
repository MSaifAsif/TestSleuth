package dev.testsleuth.maven;

import dev.testsleuth.core.event.TestSleuthRunContext;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;

final class MavenRunContextFactory {
    static final String CONTEXT_FILE_NAME = "run-context.properties";

    TestSleuthRunContext create(MavenProject project, Properties userProperties) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(userProperties, "userProperties");

        String buildRunId = userProperties.getProperty(TestSleuthRunContext.BUILD_RUN_ID_PROPERTY);
        if (buildRunId == null || buildRunId.isBlank()) {
            buildRunId = UUID.randomUUID().toString();
            userProperties.setProperty(TestSleuthRunContext.BUILD_RUN_ID_PROPERTY, buildRunId);
        }

        return new TestSleuthRunContext(
                buildRunId,
                moduleId(project),
                text(project.getGroupId()),
                text(project.getArtifactId()),
                text(project.getVersion()),
                project.getBasedir() == null ? TestSleuthRunContext.UNKNOWN : project.getBasedir().toPath().toString(),
                currentProcessId(),
                TestSleuthRunContext.UNKNOWN
        );
    }

    TestSleuthRunContext loadOrCreate(Path outputDirectory, MavenProject project, Properties userProperties) {
        Path contextFile = outputDirectory.resolve(CONTEXT_FILE_NAME);
        if (!Files.isRegularFile(contextFile)) {
            return create(project, userProperties);
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(contextFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read TestSleuth run context " + contextFile, e);
        }

        String buildRunId = properties.getProperty(TestSleuthRunContext.BUILD_RUN_ID_PROPERTY);
        if (buildRunId != null && !buildRunId.isBlank()) {
            userProperties.setProperty(TestSleuthRunContext.BUILD_RUN_ID_PROPERTY, buildRunId);
        }

        return new TestSleuthRunContext(
                buildRunId,
                properties.getProperty(TestSleuthRunContext.MODULE_ID_PROPERTY),
                properties.getProperty(TestSleuthRunContext.PROJECT_GROUP_ID_PROPERTY),
                properties.getProperty(TestSleuthRunContext.PROJECT_ARTIFACT_ID_PROPERTY),
                properties.getProperty(TestSleuthRunContext.PROJECT_VERSION_PROPERTY),
                properties.getProperty(TestSleuthRunContext.PROJECT_BASE_DIR_PROPERTY),
                currentProcessId(),
                TestSleuthRunContext.UNKNOWN
        );
    }

    void write(Path outputDirectory, TestSleuthRunContext context) {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(context, "context");

        Properties properties = new Properties();
        properties.setProperty(TestSleuthRunContext.BUILD_RUN_ID_PROPERTY, context.buildRunId());
        properties.setProperty(TestSleuthRunContext.MODULE_ID_PROPERTY, context.moduleId());
        properties.setProperty(TestSleuthRunContext.PROJECT_GROUP_ID_PROPERTY, context.projectGroupId());
        properties.setProperty(TestSleuthRunContext.PROJECT_ARTIFACT_ID_PROPERTY, context.projectArtifactId());
        properties.setProperty(TestSleuthRunContext.PROJECT_VERSION_PROPERTY, context.projectVersion());
        properties.setProperty(TestSleuthRunContext.PROJECT_BASE_DIR_PROPERTY, context.projectBaseDir());

        try {
            Files.createDirectories(outputDirectory);
            try (Writer writer = Files.newBufferedWriter(
                    outputDirectory.resolve(CONTEXT_FILE_NAME),
                    StandardCharsets.UTF_8
            )) {
                properties.store(writer, "TestSleuth run context");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write TestSleuth run context to " + outputDirectory, e);
        }
    }

    private static String moduleId(MavenProject project) {
        return text(project.getGroupId()) + ":" + text(project.getArtifactId());
    }

    private static String text(String value) {
        if (value == null || value.isBlank()) {
            return TestSleuthRunContext.UNKNOWN;
        }
        return value.trim();
    }

    private static String currentProcessId() {
        return Long.toString(ProcessHandle.current().pid());
    }
}

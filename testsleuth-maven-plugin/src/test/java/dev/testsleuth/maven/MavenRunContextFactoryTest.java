package dev.testsleuth.maven;

import dev.testsleuth.core.event.TestSleuthRunContext;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MavenRunContextFactoryTest {
    @TempDir
    private Path tempDir;

    @Test
    void createsAndPersistsModuleRunContext() {
        MavenProject project = project();
        Properties userProperties = new Properties();
        MavenRunContextFactory factory = new MavenRunContextFactory();

        TestSleuthRunContext context = factory.create(project, userProperties);
        factory.write(tempDir, context);
        TestSleuthRunContext loaded = factory.loadOrCreate(tempDir, project, new Properties());

        assertEquals(context.buildRunId(), loaded.buildRunId());
        assertEquals("dev.testsleuth:sample", loaded.moduleId());
        assertEquals("dev.testsleuth", loaded.projectGroupId());
        assertEquals("sample", loaded.projectArtifactId());
        assertTrue(tempDir.resolve(MavenRunContextFactory.CONTEXT_FILE_NAME).toFile().isFile());
    }

    @Test
    void reusesExistingBuildRunIdFromUserProperties() {
        Properties userProperties = new Properties();
        userProperties.setProperty(TestSleuthRunContext.BUILD_RUN_ID_PROPERTY, "run-1");

        TestSleuthRunContext context = new MavenRunContextFactory().create(project(), userProperties);

        assertEquals("run-1", context.buildRunId());
    }

    private static MavenProject project() {
        Model model = new Model();
        model.setGroupId("dev.testsleuth");
        model.setArtifactId("sample");
        model.setVersion("0.1.0-SNAPSHOT");
        MavenProject project = new MavenProject(model);
        project.setFile(new File("/workspace/sample/pom.xml"));
        return project;
    }
}

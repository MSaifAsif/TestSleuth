package dev.testsleuth.maven;

import dev.testsleuth.junit5.TestSleuthJUnit5Listener;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MavenTestInstrumentationTest {
    @Test
    void addsJUnitListenerDependencyAndProperties() {
        MavenProject project = new MavenProject(new Model());
        Properties userProperties = new Properties();
        Path eventsFile = Path.of("target/testsleuth/junit-events.json");

        MavenTestInstrumentation.Result result = new MavenTestInstrumentation()
                .apply(project, userProperties, eventsFile, "0.1.0-SNAPSHOT");

        assertTrue(result.dependencyAdded());
        assertEquals(eventsFile, result.eventsFile());
        assertEquals("true", userProperties.getProperty(MavenTestInstrumentation.JUNIT_LISTENER_AUTODETECTION_PROPERTY));
        assertEquals(eventsFile.toString(), userProperties.getProperty(TestSleuthJUnit5Listener.EVENTS_FILE_PROPERTY));
        assertEquals(1, project.getDependencies().size());
        Dependency dependency = project.getDependencies().get(0);
        assertEquals("dev.testsleuth", dependency.getGroupId());
        assertEquals("testsleuth-junit5", dependency.getArtifactId());
        assertEquals("0.1.0-SNAPSHOT", dependency.getVersion());
        assertEquals("test", dependency.getScope());
    }

    @Test
    void doesNotDuplicateExistingJUnitListenerDependency() {
        MavenProject project = new MavenProject(new Model());
        Dependency existing = new Dependency();
        existing.setGroupId("dev.testsleuth");
        existing.setArtifactId("testsleuth-junit5");
        existing.setVersion("0.1.0-SNAPSHOT");
        existing.setScope("test");
        project.getDependencies().add(existing);

        MavenTestInstrumentation.Result result = new MavenTestInstrumentation()
                .apply(project, new Properties(), Path.of("target/testsleuth/junit-events.json"), "0.1.0-SNAPSHOT");

        assertFalse(result.dependencyAdded());
        assertEquals(1, project.getDependencies().size());
    }
}


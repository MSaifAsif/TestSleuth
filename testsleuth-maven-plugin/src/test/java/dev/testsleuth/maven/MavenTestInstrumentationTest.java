package dev.testsleuth.maven;

import dev.testsleuth.junit5.TestSleuthJUnit5Listener;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
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
        String additionalClasspath = userProperties.getProperty(MavenTestInstrumentation.MAVEN_TEST_ADDITIONAL_CLASSPATH_PROPERTY);
        assertTrue(additionalClasspath.contains("testsleuth-junit5"));
        assertTrue(additionalClasspath.contains("testsleuth-core"));
        assertEquals(1, project.getDependencies().size());
        Dependency dependency = project.getDependencies().get(0);
        assertEquals("dev.testsleuth", dependency.getGroupId());
        assertEquals("testsleuth-junit5", dependency.getArtifactId());
        assertEquals("0.1.0-SNAPSHOT", dependency.getVersion());
        assertEquals("test", dependency.getScope());

        Plugin surefire = project.getBuild().getPluginsAsMap().get("org.apache.maven.plugins:maven-surefire-plugin");
        Xpp3Dom configuration = (Xpp3Dom) surefire.getConfiguration();
        assertEquals("true", configuration.getChild("systemPropertyVariables")
                .getChild(MavenTestInstrumentation.JUNIT_LISTENER_AUTODETECTION_PROPERTY)
                .getValue());
        assertEquals(eventsFile.toString(), configuration.getChild("systemPropertyVariables")
                .getChild(TestSleuthJUnit5Listener.EVENTS_FILE_PROPERTY)
                .getValue());
        assertEquals("testsleuth-junit5", configuration.getChild("additionalClasspathDependencies")
                .getChild("additionalClasspathDependency")
                .getChild("artifactId")
                .getValue());
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

    @Test
    void preservesExistingSurefireVersionWhenConfiguringPlugin() {
        MavenProject project = new MavenProject(new Model());
        Build build = new Build();
        Plugin surefire = new Plugin();
        surefire.setGroupId("org.apache.maven.plugins");
        surefire.setArtifactId("maven-surefire-plugin");
        surefire.setVersion("3.3.1");
        build.addPlugin(surefire);
        project.setBuild(build);

        new MavenTestInstrumentation()
                .apply(project, new Properties(), Path.of("target/testsleuth/junit-events.json"), "0.1.0-SNAPSHOT");

        assertEquals("3.3.1", surefire.getVersion());
    }
}

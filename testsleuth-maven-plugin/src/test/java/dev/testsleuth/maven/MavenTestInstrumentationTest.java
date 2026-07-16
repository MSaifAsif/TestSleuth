package dev.testsleuth.maven;

import dev.testsleuth.core.event.TestSleuthRunContext;
import dev.testsleuth.junit4.TestSleuthJUnit4RunListener;
import dev.testsleuth.junit5.TestSleuthJUnit5Extension;
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
        Path junit5EventsFile = Path.of("target/testsleuth/junit-events.json");
        Path junit4EventsFile = Path.of("target/testsleuth/junit4-events.json");
        TestSleuthRunContext runContext = runContext();

        MavenTestInstrumentation.Result result = new MavenTestInstrumentation()
                .apply(project, userProperties, junit5EventsFile, junit4EventsFile, "0.1.0-SNAPSHOT", runContext);

        assertTrue(result.dependencyAdded());
        assertEquals(junit5EventsFile, result.junit5EventsFile());
        assertEquals(junit4EventsFile, result.junit4EventsFile());
        assertEquals("true", userProperties.getProperty(MavenTestInstrumentation.JUNIT_LISTENER_AUTODETECTION_PROPERTY));
        assertEquals("true", userProperties.getProperty(MavenTestInstrumentation.JUNIT_JUPITER_EXTENSION_AUTODETECTION_PROPERTY));
        assertEquals(junit5EventsFile.toString(), userProperties.getProperty(TestSleuthJUnit5Listener.EVENTS_FILE_PROPERTY));
        assertEquals(junit4EventsFile.toString(), userProperties.getProperty(TestSleuthJUnit4RunListener.EVENTS_FILE_PROPERTY));
        assertEquals("run-1", userProperties.getProperty(TestSleuthRunContext.BUILD_RUN_ID_PROPERTY));
        assertEquals("dev.testsleuth:sample", userProperties.getProperty(TestSleuthRunContext.MODULE_ID_PROPERTY));
        String additionalClasspath = userProperties.getProperty(MavenTestInstrumentation.MAVEN_TEST_ADDITIONAL_CLASSPATH_PROPERTY);
        assertTrue(additionalClasspath.contains("testsleuth-junit5"));
        assertTrue(additionalClasspath.contains("testsleuth-junit4"));
        assertTrue(additionalClasspath.contains("testsleuth-core"));
        assertEquals(2, project.getDependencies().size());
        assertInjectedDependency(project, "testsleuth-junit5");
        assertInjectedDependency(project, "testsleuth-junit4");

        Plugin surefire = project.getBuild().getPluginsAsMap().get("org.apache.maven.plugins:maven-surefire-plugin");
        Xpp3Dom configuration = (Xpp3Dom) surefire.getConfiguration();
        assertEquals("true", configuration.getChild("systemPropertyVariables")
                .getChild(MavenTestInstrumentation.JUNIT_LISTENER_AUTODETECTION_PROPERTY)
                .getValue());
        assertEquals("true", configuration.getChild("systemPropertyVariables")
                .getChild(TestSleuthJUnit5Extension.AUTODETECTION_PROPERTY)
                .getValue());
        assertEquals(junit5EventsFile.toString(), configuration.getChild("systemPropertyVariables")
                .getChild(TestSleuthJUnit5Listener.EVENTS_FILE_PROPERTY)
                .getValue());
        assertEquals(junit4EventsFile.toString(), configuration.getChild("systemPropertyVariables")
                .getChild(TestSleuthJUnit4RunListener.EVENTS_FILE_PROPERTY)
                .getValue());
        assertEquals("run-1", configuration.getChild("systemPropertyVariables")
                .getChild(TestSleuthRunContext.BUILD_RUN_ID_PROPERTY)
                .getValue());
        assertEquals("${surefire.forkNumber}", configuration.getChild("systemPropertyVariables")
                .getChild(TestSleuthRunContext.FORK_NUMBER_PROPERTY)
                .getValue());
        assertAdditionalClasspathDependency(configuration, "testsleuth-junit5");
        assertAdditionalClasspathDependency(configuration, "testsleuth-junit4");
        assertEquals(TestSleuthJUnit4RunListener.class.getName(), configuration.getChild("properties")
                .getChild("property")
                .getChild("value")
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
                .apply(
                        project,
                        new Properties(),
                        Path.of("target/testsleuth/junit-events.json"),
                        Path.of("target/testsleuth/junit4-events.json"),
                        "0.1.0-SNAPSHOT",
                        runContext()
                );

        assertTrue(result.dependencyAdded());
        assertEquals(2, project.getDependencies().size());
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
                .apply(
                        project,
                        new Properties(),
                        Path.of("target/testsleuth/junit-events.json"),
                        Path.of("target/testsleuth/junit4-events.json"),
                        "0.1.0-SNAPSHOT",
                        runContext()
                );

        assertEquals("3.3.1", surefire.getVersion());
    }

    @Test
    void appendsJUnit4RunListenerWithoutReplacingExistingSurefireListener() {
        MavenProject project = new MavenProject(new Model());
        Build build = new Build();
        Plugin surefire = new Plugin();
        surefire.setGroupId("org.apache.maven.plugins");
        surefire.setArtifactId("maven-surefire-plugin");
        Xpp3Dom configuration = new Xpp3Dom("configuration");
        Xpp3Dom properties = new Xpp3Dom("properties");
        Xpp3Dom listener = new Xpp3Dom("property");
        Xpp3Dom name = new Xpp3Dom("name");
        name.setValue("listener");
        Xpp3Dom value = new Xpp3Dom("value");
        value.setValue("com.example.ExistingListener");
        listener.addChild(name);
        listener.addChild(value);
        properties.addChild(listener);
        configuration.addChild(properties);
        surefire.setConfiguration(configuration);
        build.addPlugin(surefire);
        project.setBuild(build);

        new MavenTestInstrumentation()
                .apply(
                        project,
                        new Properties(),
                        Path.of("target/testsleuth/junit-events.json"),
                        Path.of("target/testsleuth/junit4-events.json"),
                        "0.1.0-SNAPSHOT",
                        runContext()
                );
        new MavenTestInstrumentation()
                .apply(
                        project,
                        new Properties(),
                        Path.of("target/testsleuth/junit-events.json"),
                        Path.of("target/testsleuth/junit4-events.json"),
                        "0.1.0-SNAPSHOT",
                        runContext()
                );

        String configuredListener = configuration.getChild("properties")
                .getChild("property")
                .getChild("value")
                .getValue();
        assertEquals("com.example.ExistingListener," + TestSleuthJUnit4RunListener.class.getName(), configuredListener);
    }

    private static void assertInjectedDependency(MavenProject project, String artifactId) {
        Dependency dependency = project.getDependencies().stream()
                .filter(candidate -> artifactId.equals(candidate.getArtifactId()))
                .findFirst()
                .orElseThrow();
        assertEquals("dev.testsleuth", dependency.getGroupId());
        assertEquals("0.1.0-SNAPSHOT", dependency.getVersion());
        assertEquals("test", dependency.getScope());
    }

    private static void assertAdditionalClasspathDependency(Xpp3Dom configuration, String artifactId) {
        Xpp3Dom dependencies = configuration.getChild("additionalClasspathDependencies");
        for (Xpp3Dom dependency : dependencies.getChildren("additionalClasspathDependency")) {
            Xpp3Dom artifactIdNode = dependency.getChild("artifactId");
            if (artifactIdNode != null && artifactId.equals(artifactIdNode.getValue())) {
                return;
            }
        }
        throw new AssertionError("Missing additionalClasspathDependency " + artifactId);
    }

    private static TestSleuthRunContext runContext() {
        return new TestSleuthRunContext(
                "run-1",
                "dev.testsleuth:sample",
                "dev.testsleuth",
                "sample",
                "0.1.0-SNAPSHOT",
                "/workspace/sample",
                "123",
                "unknown"
        );
    }
}

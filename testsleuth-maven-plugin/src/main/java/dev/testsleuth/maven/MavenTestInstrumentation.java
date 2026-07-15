package dev.testsleuth.maven;

import dev.testsleuth.core.event.EventJsonWriter;
import dev.testsleuth.core.event.TestSleuthRunContext;
import dev.testsleuth.junit5.TestSleuthJUnit5Listener;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.nio.file.Path;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

final class MavenTestInstrumentation {
    static final String JUNIT_LISTENER_AUTODETECTION_PROPERTY = "junit.platform.listeners.autodetection.enabled";
    static final String TESTSLEUTH_JUNIT5_GROUP_ID = "dev.testsleuth";
    static final String TESTSLEUTH_JUNIT5_ARTIFACT_ID = "testsleuth-junit5";
    static final String MAVEN_TEST_ADDITIONAL_CLASSPATH_PROPERTY = "maven.test.additionalClasspath";
    private static final String MAVEN_PLUGIN_GROUP_ID = "org.apache.maven.plugins";
    private static final String SUREFIRE_ARTIFACT_ID = "maven-surefire-plugin";
    private static final String FAILSAFE_ARTIFACT_ID = "maven-failsafe-plugin";
    private static final String DEFAULT_TEST_PLUGIN_VERSION = "3.2.5";

    Result apply(
            MavenProject project,
            Properties userProperties,
            Path eventsFile,
            String testSleuthVersion,
            TestSleuthRunContext runContext
    ) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(userProperties, "userProperties");
        Objects.requireNonNull(eventsFile, "eventsFile");
        Objects.requireNonNull(runContext, "runContext");
        requireText(testSleuthVersion, "testSleuthVersion");

        boolean dependencyAdded = ensureJUnit5Dependency(project, testSleuthVersion);
        userProperties.setProperty(JUNIT_LISTENER_AUTODETECTION_PROPERTY, "true");
        userProperties.setProperty(TestSleuthJUnit5Listener.EVENTS_FILE_PROPERTY, eventsFile.toString());
        setUserProperties(userProperties, runContext);
        appendAdditionalClasspath(userProperties);
        configureTestPlugin(project, SUREFIRE_ARTIFACT_ID, eventsFile, testSleuthVersion, runContext);
        configureTestPlugin(project, FAILSAFE_ARTIFACT_ID, eventsFile, testSleuthVersion, runContext);

        return new Result(dependencyAdded, eventsFile);
    }

    private static boolean ensureJUnit5Dependency(MavenProject project, String testSleuthVersion) {
        boolean alreadyPresent = project.getDependencies().stream()
                .anyMatch(dependency -> TESTSLEUTH_JUNIT5_GROUP_ID.equals(dependency.getGroupId())
                        && TESTSLEUTH_JUNIT5_ARTIFACT_ID.equals(dependency.getArtifactId()));

        if (alreadyPresent) {
            return false;
        }

        Dependency dependency = new Dependency();
        dependency.setGroupId(TESTSLEUTH_JUNIT5_GROUP_ID);
        dependency.setArtifactId(TESTSLEUTH_JUNIT5_ARTIFACT_ID);
        dependency.setVersion(testSleuthVersion);
        dependency.setScope("test");
        project.getDependencies().add(dependency);
        return true;
    }

    private static void configureTestPlugin(
            MavenProject project,
            String artifactId,
            Path eventsFile,
            String testSleuthVersion,
            TestSleuthRunContext runContext
    ) {
        Plugin plugin = findOrCreateBuildPlugin(project, artifactId);
        Xpp3Dom configuration = configuration(plugin);
        Xpp3Dom systemProperties = child(configuration, "systemPropertyVariables");
        setChildValue(systemProperties, JUNIT_LISTENER_AUTODETECTION_PROPERTY, "true");
        setChildValue(systemProperties, TestSleuthJUnit5Listener.EVENTS_FILE_PROPERTY, eventsFile.toString());
        setContextSystemProperties(systemProperties, runContext);

        Xpp3Dom dependencies = child(configuration, "additionalClasspathDependencies");
        if (!hasTestSleuthDependency(dependencies)) {
            Xpp3Dom dependency = new Xpp3Dom("additionalClasspathDependency");
            setChildValue(dependency, "groupId", TESTSLEUTH_JUNIT5_GROUP_ID);
            setChildValue(dependency, "artifactId", TESTSLEUTH_JUNIT5_ARTIFACT_ID);
            setChildValue(dependency, "version", testSleuthVersion);
            dependencies.addChild(dependency);
        }
    }

    private static Plugin findOrCreateBuildPlugin(MavenProject project, String artifactId) {
        Build build = project.getBuild();
        if (build == null) {
            build = new Build();
            project.setBuild(build);
        }

        for (Plugin plugin : build.getPlugins()) {
            if (MAVEN_PLUGIN_GROUP_ID.equals(plugin.getGroupId()) && artifactId.equals(plugin.getArtifactId())) {
                return plugin;
            }
        }

        Plugin plugin = new Plugin();
        plugin.setGroupId(MAVEN_PLUGIN_GROUP_ID);
        plugin.setArtifactId(artifactId);
        plugin.setVersion(DEFAULT_TEST_PLUGIN_VERSION);
        build.addPlugin(plugin);
        return plugin;
    }

    private static Xpp3Dom configuration(Plugin plugin) {
        Object existing = plugin.getConfiguration();
        if (existing instanceof Xpp3Dom dom) {
            return dom;
        }
        Xpp3Dom configuration = new Xpp3Dom("configuration");
        plugin.setConfiguration(configuration);
        return configuration;
    }

    private static Xpp3Dom child(Xpp3Dom parent, String name) {
        Xpp3Dom child = parent.getChild(name);
        if (child == null) {
            child = new Xpp3Dom(name);
            parent.addChild(child);
        }
        return child;
    }

    private static void setChildValue(Xpp3Dom parent, String name, String value) {
        Xpp3Dom child = child(parent, name);
        child.setValue(value);
    }

    private static boolean hasTestSleuthDependency(Xpp3Dom dependencies) {
        for (Xpp3Dom child : dependencies.getChildren()) {
            Xpp3Dom groupId = child.getChild("groupId");
            Xpp3Dom artifactId = child.getChild("artifactId");
            if (groupId != null
                    && artifactId != null
                    && TESTSLEUTH_JUNIT5_GROUP_ID.equals(groupId.getValue())
                    && TESTSLEUTH_JUNIT5_ARTIFACT_ID.equals(artifactId.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static void setUserProperties(Properties userProperties, TestSleuthRunContext runContext) {
        userProperties.setProperty(TestSleuthRunContext.BUILD_RUN_ID_PROPERTY, runContext.buildRunId());
        userProperties.setProperty(TestSleuthRunContext.MODULE_ID_PROPERTY, runContext.moduleId());
        userProperties.setProperty(TestSleuthRunContext.PROJECT_GROUP_ID_PROPERTY, runContext.projectGroupId());
        userProperties.setProperty(TestSleuthRunContext.PROJECT_ARTIFACT_ID_PROPERTY, runContext.projectArtifactId());
        userProperties.setProperty(TestSleuthRunContext.PROJECT_VERSION_PROPERTY, runContext.projectVersion());
        userProperties.setProperty(TestSleuthRunContext.PROJECT_BASE_DIR_PROPERTY, runContext.projectBaseDir());
    }

    private static void setContextSystemProperties(Xpp3Dom systemProperties, TestSleuthRunContext runContext) {
        setChildValue(systemProperties, TestSleuthRunContext.BUILD_RUN_ID_PROPERTY, runContext.buildRunId());
        setChildValue(systemProperties, TestSleuthRunContext.MODULE_ID_PROPERTY, runContext.moduleId());
        setChildValue(systemProperties, TestSleuthRunContext.PROJECT_GROUP_ID_PROPERTY, runContext.projectGroupId());
        setChildValue(systemProperties, TestSleuthRunContext.PROJECT_ARTIFACT_ID_PROPERTY, runContext.projectArtifactId());
        setChildValue(systemProperties, TestSleuthRunContext.PROJECT_VERSION_PROPERTY, runContext.projectVersion());
        setChildValue(systemProperties, TestSleuthRunContext.PROJECT_BASE_DIR_PROPERTY, runContext.projectBaseDir());
        setChildValue(systemProperties, TestSleuthRunContext.FORK_NUMBER_PROPERTY, "${surefire.forkNumber}");
    }

    private static void appendAdditionalClasspath(Properties userProperties) {
        Set<String> elements = new LinkedHashSet<>();
        String existing = userProperties.getProperty(MAVEN_TEST_ADDITIONAL_CLASSPATH_PROPERTY);
        if (existing != null && !existing.isBlank()) {
            for (String value : existing.split(",")) {
                if (!value.isBlank()) {
                    elements.add(value.trim());
                }
            }
        }

        elements.add(codeSourcePath(TestSleuthJUnit5Listener.class));
        elements.add(codeSourcePath(EventJsonWriter.class));
        userProperties.setProperty(
                MAVEN_TEST_ADDITIONAL_CLASSPATH_PROPERTY,
                elements.stream().collect(Collectors.joining(","))
        );
    }

    private static String codeSourcePath(Class<?> type) {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            throw new IllegalStateException("Unable to locate code source for " + type.getName());
        }
        try {
            return Path.of(codeSource.getLocation().toURI()).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Unable to locate code source for " + type.getName(), e);
        }
    }

    private static void requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    record Result(boolean dependencyAdded, Path eventsFile) {
    }
}

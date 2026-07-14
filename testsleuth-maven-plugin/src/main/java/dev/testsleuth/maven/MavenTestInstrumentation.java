package dev.testsleuth.maven;

import dev.testsleuth.junit5.TestSleuthJUnit5Listener;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

final class MavenTestInstrumentation {
    static final String JUNIT_LISTENER_AUTODETECTION_PROPERTY = "junit.platform.listeners.autodetection.enabled";
    static final String TESTSLEUTH_JUNIT5_GROUP_ID = "dev.testsleuth";
    static final String TESTSLEUTH_JUNIT5_ARTIFACT_ID = "testsleuth-junit5";

    Result apply(MavenProject project, Properties userProperties, Path eventsFile, String testSleuthVersion) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(userProperties, "userProperties");
        Objects.requireNonNull(eventsFile, "eventsFile");
        requireText(testSleuthVersion, "testSleuthVersion");

        boolean dependencyAdded = ensureJUnit5Dependency(project, testSleuthVersion);
        userProperties.setProperty(JUNIT_LISTENER_AUTODETECTION_PROPERTY, "true");
        userProperties.setProperty(TestSleuthJUnit5Listener.EVENTS_FILE_PROPERTY, eventsFile.toString());

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

    private static void requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    record Result(boolean dependencyAdded, Path eventsFile) {
    }
}


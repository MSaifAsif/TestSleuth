package dev.testsleuth.maven;

import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class MavenTestRunnerContext {
    private static final String MAVEN_PLUGIN_GROUP_ID = "org.apache.maven.plugins";
    private static final String SUREFIRE_ARTIFACT_ID = "maven-surefire-plugin";
    private static final String FAILSAFE_ARTIFACT_ID = "maven-failsafe-plugin";

    List<MavenTestReportScanner.ReportDirectory> reportDirectories(MavenProject project, Path buildDirectory) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(buildDirectory, "buildDirectory");

        return List.of(
                new MavenTestReportScanner.ReportDirectory(
                        buildDirectory.resolve("surefire-reports"),
                        attributes("surefire", findPlugin(project, SUREFIRE_ARTIFACT_ID))
                ),
                new MavenTestReportScanner.ReportDirectory(
                        buildDirectory.resolve("failsafe-reports"),
                        attributes("failsafe", findPlugin(project, FAILSAFE_ARTIFACT_ID))
                )
        );
    }

    private static Map<String, String> attributes(String testRunner, Plugin plugin) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("testRunner", testRunner);
        attributes.put("testPluginGroupId", MAVEN_PLUGIN_GROUP_ID);
        attributes.put("testPluginArtifactId", artifactId(testRunner));
        attributes.put("testPluginVersion", plugin == null || blank(plugin.getVersion()) ? "unknown" : plugin.getVersion().trim());

        Xpp3Dom configuration = configuration(plugin);
        putConfig(attributes, configuration, "forkCount");
        putConfig(attributes, configuration, "reuseForks");
        putConfig(attributes, configuration, "parallel");
        putConfig(attributes, configuration, "threadCount");
        return Map.copyOf(attributes);
    }

    private static String artifactId(String testRunner) {
        return switch (testRunner) {
            case "failsafe" -> FAILSAFE_ARTIFACT_ID;
            case "surefire" -> SUREFIRE_ARTIFACT_ID;
            default -> "unknown";
        };
    }

    private static Plugin findPlugin(MavenProject project, String artifactId) {
        Build build = project.getBuild();
        if (build == null) {
            return null;
        }
        for (Plugin plugin : build.getPlugins()) {
            if (MAVEN_PLUGIN_GROUP_ID.equals(plugin.getGroupId()) && artifactId.equals(plugin.getArtifactId())) {
                return plugin;
            }
        }
        return null;
    }

    private static Xpp3Dom configuration(Plugin plugin) {
        if (plugin == null || !(plugin.getConfiguration() instanceof Xpp3Dom configuration)) {
            return null;
        }
        return configuration;
    }

    private static void putConfig(Map<String, String> attributes, Xpp3Dom configuration, String name) {
        String value = childValue(configuration, name);
        if (!blank(value)) {
            attributes.put("testPlugin." + name, value.trim());
        }
    }

    private static String childValue(Xpp3Dom configuration, String name) {
        if (configuration == null || configuration.getChild(name) == null) {
            return null;
        }
        return configuration.getChild(name).getValue();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

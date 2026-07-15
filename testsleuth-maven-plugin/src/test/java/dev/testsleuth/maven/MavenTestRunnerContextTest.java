package dev.testsleuth.maven;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MavenTestRunnerContextTest {
    @Test
    void extractsSurefireAndFailsafeRunnerConfiguration() {
        MavenProject project = new MavenProject(new Model());
        Build build = new Build();
        build.addPlugin(plugin("maven-surefire-plugin", "3.2.5", Map.of(
                "forkCount", "2",
                "reuseForks", "false",
                "parallel", "classes",
                "threadCount", "4"
        )));
        build.addPlugin(plugin("maven-failsafe-plugin", "3.2.5", Map.of(
                "forkCount", "1C"
        )));
        project.setBuild(build);

        List<MavenTestReportScanner.ReportDirectory> directories = new MavenTestRunnerContext()
                .reportDirectories(project, Path.of("target"));

        MavenTestReportScanner.ReportDirectory surefire = directories.get(0);
        assertEquals(Path.of("target/surefire-reports"), surefire.path());
        assertEquals("surefire", surefire.attributes().get("testRunner"));
        assertEquals("maven-surefire-plugin", surefire.attributes().get("testPluginArtifactId"));
        assertEquals("3.2.5", surefire.attributes().get("testPluginVersion"));
        assertEquals("2", surefire.attributes().get("testPlugin.forkCount"));
        assertEquals("false", surefire.attributes().get("testPlugin.reuseForks"));
        assertEquals("classes", surefire.attributes().get("testPlugin.parallel"));
        assertEquals("4", surefire.attributes().get("testPlugin.threadCount"));

        MavenTestReportScanner.ReportDirectory failsafe = directories.get(1);
        assertEquals(Path.of("target/failsafe-reports"), failsafe.path());
        assertEquals("failsafe", failsafe.attributes().get("testRunner"));
        assertEquals("maven-failsafe-plugin", failsafe.attributes().get("testPluginArtifactId"));
        assertEquals("1C", failsafe.attributes().get("testPlugin.forkCount"));
    }

    private static Plugin plugin(String artifactId, String version, Map<String, String> configurationValues) {
        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId(artifactId);
        plugin.setVersion(version);

        Xpp3Dom configuration = new Xpp3Dom("configuration");
        configurationValues.forEach((name, value) -> {
            Xpp3Dom child = new Xpp3Dom(name);
            child.setValue(value);
            configuration.addChild(child);
        });
        plugin.setConfiguration(configuration);
        return plugin;
    }
}

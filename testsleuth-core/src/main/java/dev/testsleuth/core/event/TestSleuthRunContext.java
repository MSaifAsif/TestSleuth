package dev.testsleuth.core.event;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public record TestSleuthRunContext(
        String buildRunId,
        String moduleId,
        String projectGroupId,
        String projectArtifactId,
        String projectVersion,
        String projectBaseDir,
        String processId,
        String forkNumber
) {
    public static final String BUILD_RUN_ID_PROPERTY = "testsleuth.buildRunId";
    public static final String MODULE_ID_PROPERTY = "testsleuth.moduleId";
    public static final String PROJECT_GROUP_ID_PROPERTY = "testsleuth.project.groupId";
    public static final String PROJECT_ARTIFACT_ID_PROPERTY = "testsleuth.project.artifactId";
    public static final String PROJECT_VERSION_PROPERTY = "testsleuth.project.version";
    public static final String PROJECT_BASE_DIR_PROPERTY = "testsleuth.project.baseDir";
    public static final String PROCESS_ID_PROPERTY = "testsleuth.processId";
    public static final String FORK_NUMBER_PROPERTY = "testsleuth.forkNumber";
    public static final String UNKNOWN = "unknown";

    public TestSleuthRunContext {
        buildRunId = normalize(buildRunId);
        moduleId = normalize(moduleId);
        projectGroupId = normalize(projectGroupId);
        projectArtifactId = normalize(projectArtifactId);
        projectVersion = normalize(projectVersion);
        projectBaseDir = normalize(projectBaseDir);
        processId = normalize(processId);
        forkNumber = normalize(forkNumber);
    }

    public static TestSleuthRunContext fromSystemProperties() {
        Properties properties = System.getProperties();
        return new TestSleuthRunContext(
                properties.getProperty(BUILD_RUN_ID_PROPERTY),
                properties.getProperty(MODULE_ID_PROPERTY),
                properties.getProperty(PROJECT_GROUP_ID_PROPERTY),
                properties.getProperty(PROJECT_ARTIFACT_ID_PROPERTY),
                properties.getProperty(PROJECT_VERSION_PROPERTY),
                properties.getProperty(PROJECT_BASE_DIR_PROPERTY),
                currentProcessId(),
                properties.getProperty(FORK_NUMBER_PROPERTY)
        );
    }

    public Map<String, String> attributes() {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("buildRunId", buildRunId);
        attributes.put("moduleId", moduleId);
        attributes.put("projectGroupId", projectGroupId);
        attributes.put("projectArtifactId", projectArtifactId);
        attributes.put("projectVersion", projectVersion);
        attributes.put("projectBaseDir", projectBaseDir);
        attributes.put("processId", processId);
        attributes.put("forkNumber", forkNumber);
        return Map.copyOf(attributes);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        return value.trim();
    }

    private static String currentProcessId() {
        try {
            return Long.toString(ProcessHandle.current().pid());
        } catch (RuntimeException e) {
            return ManagementFactory.getRuntimeMXBean().getName();
        }
    }
}

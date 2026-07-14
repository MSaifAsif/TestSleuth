package dev.testsleuth.maven;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

final class TestSleuthPluginMetadata {
    private static final String RESOURCE_NAME = "/dev/testsleuth/maven/testsleuth-plugin.properties";

    private final Properties properties;

    private TestSleuthPluginMetadata(Properties properties) {
        this.properties = properties;
    }

    static TestSleuthPluginMetadata load() {
        Properties properties = new Properties();
        try (InputStream input = TestSleuthPluginMetadata.class.getResourceAsStream(RESOURCE_NAME)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException e) {
            return new TestSleuthPluginMetadata(new Properties());
        }
        return new TestSleuthPluginMetadata(properties);
    }

    Optional<String> version() {
        String version = properties.getProperty("version");
        if (version == null || version.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(version);
    }
}


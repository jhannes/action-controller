package org.actioncontroller.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Given a {@link #configDirectory}, applicationName and profiles, monitors
 * the files <code>&lt;applicationName&gt;.properties</code> and
 * <code>&lt;applicationName&gt;-&lt;profile&gt;.properties</code> for each profile
 * in the {@link #configDirectory}. May be expanded to also load
 * classpath resources <code>&lt;applicationName&gt;.properties</code> and
 *  <code>&lt;applicationName&gt;-&lt;profile&gt;.properties</code> in the future.
 */
public class ConfigDirectoryLoader implements ConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigDirectoryLoader.class);
    private final Path configDirectory;
    private final String applicationName;
    private final List<String> profiles;
    private final List<String> configurationFileNames;

    public ConfigDirectoryLoader(Path configDirectory, String applicationName, List<String> profiles) {
        this.configDirectory = configDirectory;
        this.applicationName = applicationName;
        this.profiles = profiles;
        this.configurationFileNames = new ArrayList<>();
        configurationFileNames.add(applicationName + ".properties");
        profiles.forEach(profile -> configurationFileNames.add(applicationName + "-" + profile + ".properties"));
    }

    public Map<String, String> loadConfiguration() {
        Properties properties = new Properties();
        for (Path file : getConfigurationFiles()) {
            loadConfigFile(properties, file);
        }

        Map<String,String> configuration = new HashMap<>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            configuration.put((String) entry.getKey(), (String) entry.getValue());
        }
        logger.debug("Loaded {} properties from files={}", configuration.size(), getConfigurationFiles());
        return configuration;
    }

    private List<Path> getConfigurationFiles() {
        List<Path> files = new ArrayList<>();
        if (Files.isRegularFile(configDirectory.resolve(applicationName + ".properties"))) {
            files.add(configDirectory.resolve(applicationName + ".properties"));
        }
        for (String profile : profiles) {
            Path file = configDirectory.resolve(applicationName + "-" + profile + ".properties");
            if (Files.isRegularFile(file)) {
                files.add(file);
            }
        }
        return files;
    }

    private void loadConfigFile(Properties properties, Path file) {
        try (InputStream propertiesFile = new FileInputStream(file.toFile())) {
            logger.info("Loading {}", file.toAbsolutePath());
            properties.load(propertiesFile);
        } catch (IOException e) {
            logger.info("Failed to load {}", file, e);
        }
    }

    public String describe() {
        return "directory=" + configDirectory.toAbsolutePath() + ", profiles=" + profiles + ", files=" + getConfigurationFiles();
    }

    public void watch(Consumer<Map<String, String>> configChangeListener) {
        new FileScanner(configDirectory, configurationFileNames,
                config -> configChangeListener.accept(loadConfiguration()));
    }
}

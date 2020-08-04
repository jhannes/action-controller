package org.actioncontroller.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
    private final File configDirectory;
    private final List<String> configurationFileNames;

    public ConfigDirectoryLoader(File configDirectory, String applicationName, List<String> profiles) {
        configDirectory.mkdirs();
        this.configDirectory = configDirectory;
        this.configurationFileNames = new ArrayList<>();
        configurationFileNames.add(applicationName + ".properties");
        profiles.forEach(profile -> configurationFileNames.add(applicationName + "-" + profile + ".properties"));
    }

    public Map<String, String> loadConfiguration() {
        Properties properties = new Properties();

        List<File> files = getConfigurationFiles();
        for (File file : files) {
            loadConfigFile(properties, file);
        }

        Map<String,String> configuration = new HashMap<>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            configuration.put((String) entry.getKey(), (String) entry.getValue());
        }
        logger.debug("Loaded {} properties from {}", configuration.size(), configurationFileNames);
        return configuration;
    }

    private void loadConfigFile(Properties properties, File file) {
        try (InputStream propertiesFile = new FileInputStream(file)) {
            logger.info("Loading {}", file.getAbsolutePath());
            properties.load(propertiesFile);
        } catch (IOException e) {
            logger.info("Failed to load {}", file, e);
        }
    }

    public List<String> getConfigurationFileNames() {
        return configurationFileNames;
    }

    public String describe() {
        return "directory=" + configDirectory.getAbsolutePath() + ", fileNames=" + configurationFileNames
                + ", files=" + getConfigurationFiles();
    }

    public List<File> getConfigurationFiles() {
        return configurationFileNames.stream().map(f -> new File(configDirectory, f)).filter(File::isFile).collect(Collectors.toList());
    }

    public void watch(Consumer<Map<String, String>> configChangeListener) {
        new FileScanner(configDirectory, getConfigurationFileNames(),
                config -> configChangeListener.accept(loadConfiguration()));
    }
}

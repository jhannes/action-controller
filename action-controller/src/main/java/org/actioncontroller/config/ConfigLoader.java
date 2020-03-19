package org.actioncontroller.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Given a {@link #configDirectory}, applicationName and profiles, monitors
 * the files <code>&lt;applicationName&gt;.properties</code> and
 * <code>&lt;applicationName&gt;-&lt;profile&gt;.properties</code> for each profile
 * in the {@link #configDirectory}. May be expanded to also load
 * classpath resources <code>&lt;applicationName&gt;.properties</code> and
 *  * <code>&lt;applicationName&gt;-&lt;profile&gt;.properties</code> in the future.
 */
public class ConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private final File configDirectory;
    private final List<String> configurationFileNames;

    public ConfigLoader(File configDirectory, String applicationName) {
        this.configDirectory = configDirectory;
        this.configurationFileNames = Arrays.asList(applicationName + ".properties", applicationName + "-preview.properties");
    }

    public Map<String, String> loadConfiguration() {
        Properties properties = new Properties();

        for (File file : getConfigurationFiles()) {
            loadConfigFile(properties, file);
        }

        Map<String,String> configuration = new HashMap<>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            configuration.put((String) entry.getKey(), (String) entry.getValue());
        }
        logger.debug("Loaded {} properties", configuration.size());
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
}

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

        for (String fileName : configurationFileNames) {
            loadConfigFile(properties, fileName);
        }

        Map<String,String> configuration = new HashMap<>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            configuration.put((String) entry.getKey(), (String) entry.getValue());
        }
        logger.debug("Loaded {} properties", configuration.size());
        return configuration;
    }

    private void loadConfigFile(Properties properties, String filename) {
        File file = new File(configDirectory, filename);
        if (file.isFile()) {
            try (InputStream propertiesFile = new FileInputStream(file)) {
                logger.info("Loading {}", file);
                properties.load(propertiesFile);
            } catch (IOException e) {
                logger.info("Failed to load {}", file, e);
            }
        }
    }

    public List<String> getConfigurationFileNames() {
        return configurationFileNames;
    }
}

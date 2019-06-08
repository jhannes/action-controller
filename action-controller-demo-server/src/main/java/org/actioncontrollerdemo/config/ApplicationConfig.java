package org.actioncontrollerdemo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class ApplicationConfig {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationConfig.class);

    private final Path propertiesDir;
    private final WatchService newWatchService;
    private String applicationName;
    private Map<String, String> currentConfiguration;
    private HashMap<String, List<ConfigurationConsumer<Optional<String>>>> configurationListeners = new HashMap<>();

    public ApplicationConfig(String applicationName) {
        this.applicationName = applicationName;
        this.propertiesDir = Paths.get(".").toAbsolutePath();
        this.currentConfiguration = loadConfiguration();
        try {
            newWatchService = propertiesDir.getFileSystem().newWatchService();
            propertiesDir.register(newWatchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        } catch (IOException e) {
            throw new ApplicationConfigurationException("Failed to start watcher", e);
        }
        startConfigurationFileWatcher();
    }

    public void onInetSocketAddress(String key, ConfigurationConsumer<InetSocketAddress> callback, int defaultPort) {
        onConfigurationChange(key, callback, value -> transform(value, defaultPort));
        triggerListener(key, get(key), callback, value -> transform(value, defaultPort));
    }

    private Optional<String> get(String key) {
        return Optional.ofNullable(currentConfiguration.get(key));
    }

    private InetSocketAddress transform(Optional<String> value, int defaultPort) {
        if (!value.isPresent()) {
            return new InetSocketAddress(defaultPort);
        }
        int colonPos = value.get().indexOf(':');
        if (colonPos < 0) {
            try {
                return new InetSocketAddress(Integer.parseInt(value.get()));
            } catch (NumberFormatException e) {
                return new InetSocketAddress(value.get(), defaultPort);
            }
        } else if (colonPos == 0) {
            return new InetSocketAddress(Integer.parseInt(value.get().substring(1)));
        } else {
            return new InetSocketAddress(
                    value.get().substring(0, colonPos),
                    Integer.parseInt(value.get().substring(colonPos+1))
            );
        }
    }

    private void onConfigurationChange(String key, ConfigurationConsumer<Optional<String>> callback) {
        getConfigurationListeners(key).add(callback);
    }

    private <T> void onConfigurationChange(String key, ConfigurationConsumer<T> callback, Function<Optional<String>, T> transform) {
        getConfigurationListeners(key).add(s -> {
            T value = transform.apply(s);
            logger.debug("Setting configuration {}={} for {}", key, value, callback);
            callback.accept(value);
        });
    }

    protected void startConfigurationFileWatcher() {
        Thread configurationWatcher = new Thread(() -> {
            try {
                while (true) {
                    WatchKey key = newWatchService.take();
                    List<String> watchedFiles = getConfigurationFileNames();
                    List<String> changedFiles = key.pollEvents().stream()
                            .map(e -> ((Path) e.context()).getFileName().toString())
                            .collect(Collectors.toList());
                    logger.debug("Files changes: {}", changedFiles);
                    boolean shouldReload = changedFiles.stream().anyMatch(watchedFiles::contains);
                    key.reset();
                    if (shouldReload) {
                        reload();
                    }
                }
            } catch (InterruptedException ignored) {
            }
        });
        configurationWatcher.setName(toString() + "-Watcher");
        configurationWatcher.setDaemon(true);
        configurationWatcher.start();
        logger.debug("Started {}", configurationWatcher);
    }

    private void reload() {
        Map<String, String> newConfiguration = loadConfiguration();

        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(newConfiguration.keySet());
        allKeys.addAll(currentConfiguration.keySet());

        allKeys.removeIf(key -> Objects.equals(newConfiguration.get(key), currentConfiguration.get(key)));
        this.currentConfiguration = newConfiguration;
        onConfigurationChanged(allKeys, newConfiguration);
    }

    private void onConfigurationChanged(Set<String> changeKeys, Map<String, String> newConfiguration) {
        changeKeys.forEach(key -> triggerListeners(key, Optional.ofNullable(newConfiguration.get(key))));
    }

    private void triggerListeners(String key, Optional<String> value) {
        getConfigurationListeners(key).forEach(listener -> triggerListener(key, value, listener));
    }

    private void triggerListener(String key, Optional<String> value, ConfigurationConsumer<Optional<String>> listener) {
        try {
            logger.debug("Setting configuration {}={} for {}", key, value, listener);
            listener.accept(value);
        } catch (Exception e) {
            logger.error("While updating configuration for {}={}", key, value, e);
        }
    }

    private <T> void triggerListener(String key, Optional<String> s, ConfigurationConsumer<T> listener, Function<Optional<String>, T> transform) {
        T value;
        try {
            value = transform.apply(s);
        } catch (Exception e) {
            logger.error("While updating configuration for {}={}", key, s, e);
            return;
        }
        logger.debug("Setting configuration {}={} for {}", key, value, listener);
        try {
            listener.accept(value);
        } catch (Exception e) {
            logger.error("While updating configuration for {}={}", key, value, e);
        }
    }


    private List<ConfigurationConsumer<Optional<String>>> getConfigurationListeners(String key) {
        return configurationListeners.computeIfAbsent(key, k -> new ArrayList<>());
    }

    private Map<String, String> loadConfiguration() {
        return loadConfiguration(getConfigurationFileNames());
    }

    private Map<String, String> loadConfiguration(List<String> configurationFileNames) {
        logger.info("Loading configuration from {} in {}", configurationFileNames, propertiesDir);
        Properties properties = new Properties();
        for (String filename : configurationFileNames) {
            loadConfigResource(properties, filename);
            loadConfigFile(properties, filename);
        }
        Map<String,String> configuration = new HashMap<>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            configuration.put((String) entry.getKey(), (String) entry.getValue());
        }
        logger.debug("Loaded {} properties", configuration.size());
        return configuration;
    }

    private void loadConfigFile(Properties properties, String resourceName) {
        try (InputStream propertiesFile = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (propertiesFile != null) {
                logger.info("Loading {} from classpath", resourceName);
                properties.load(propertiesFile);
            }
        } catch (IOException e) {
            logger.info("Failed to load {} from classpath", resourceName, e);
        }
    }

    private void loadConfigResource(Properties properties, String fileName) {
        Path path = this.propertiesDir.resolve(fileName).toAbsolutePath();
        if (Files.isRegularFile(path)) {
            try (InputStream propertiesFile = new FileInputStream(path.toFile())) {
                logger.info("Loading {}", path);
                properties.load(propertiesFile);
            } catch (IOException e) {
                logger.info("Failed to load {}", path, e);
            }
        }

    }

    private List<String> getConfigurationFileNames() {
        return Collections.singletonList(applicationName + ".properties");
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + applicationName + ']';
    }
}

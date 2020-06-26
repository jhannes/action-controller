package org.actioncontroller.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Used to monitor a set of configuration files and notify {@link ConfigListener}s
 * on initialization and change. Given a directory and an application name,
 * appropriate resources and files are monitored with a {@link ConfigLoader}
 */
public class ConfigObserver {
    private static final Logger logger = LoggerFactory.getLogger(ConfigObserver.class);
    private final File configDirectory;
    private final String applicationName;
    private final FileScanner fileScanner;
    private Map<String, String> currentConfiguration;
    private List<ConfigListener> listeners = new ArrayList<>();

    private ConfigLoader configLoader;

    public ConfigObserver(String applicationName) {
        this(new File("."), applicationName, new ArrayList<>());
    }

    public ConfigObserver(File configDirectory, String applicationName) {
        this(configDirectory, applicationName, new ArrayList<>());
    }

    public ConfigObserver(File configDirectory, String applicationName, List<String> profiles) {
        this.configDirectory = configDirectory;
        this.applicationName = applicationName;
        configDirectory.mkdirs();

        configLoader = new ConfigLoader(configDirectory, applicationName, profiles);
        currentConfiguration = configLoader.loadConfiguration();
        fileScanner = new FileScanner(configDirectory, configLoader.getConfigurationFileNames(), this::handleFileChanged);
    }

    /**
     * The generic observer method. Call {@link ConfigListener#onConfigChanged} on
     * the listener when this method is first called and again each time config changes.
     */
    public ConfigObserver onConfigChange(ConfigListener listener) {
        this.listeners.add(listener);
        notifyListener(listener, null, new ConfigMap(currentConfiguration));
        return this;
    }

    public <T> ConfigObserver onSingleConfigValue(String key, T defaultValue, ConfigValueListener<T> listener, Function<String, T> transformer) {
        return onConfigChange(new SingleValueConfigListener<>(key, listener, defaultValue, transformer));
    }

    public ConfigObserver onConfigValue(String key, String defaultValue, ConfigValueListener<String> listener) {
        return onSingleConfigValue(key, defaultValue, listener, Function.identity());
    }

    public ConfigObserver onIntValue(String key, int defaultValue, ConfigValueListener<Integer> listener) {
        return onSingleConfigValue(key, defaultValue, listener, Integer::parseInt);
    }

    public ConfigObserver onLongValue(String key, long defaultValue, ConfigValueListener<Long> listener) {
        return onSingleConfigValue(key, defaultValue, listener, Long::parseLong);
    }

    public ConfigObserver onInetSocketAddress(String key, ConfigValueListener<InetSocketAddress> listener, int defaultPort) {
        return onInetSocketAddress(key, listener, new InetSocketAddress(defaultPort));
    }

    public ConfigObserver onInetSocketAddress(String key, ConfigValueListener<InetSocketAddress> listener, InetSocketAddress defaultAddress) {
        return onSingleConfigValue(key, defaultAddress, listener, ConfigListener::asInetSocketAddress);
    }

    public ConfigObserver onDuration(String key, ConfigValueListener<Duration> listener) {
        return onDuration(key, null, listener);
    }

    public ConfigObserver onDuration(String key, Duration defaultValue, ConfigValueListener<Duration> listener) {
        return onSingleConfigValue(key, defaultValue, listener, Duration::parse);
    }

    public ConfigObserver onStringList(String key, String defaultValue, ConfigValueListener<List<String>> listener) {
        return onSingleConfigValue(key, defaultValue != null ?  parseStringList(defaultValue) : null, listener, ConfigObserver::parseStringList);
    }

    public <T> ConfigObserver onOptionalPrefixedValue(String prefix, ConfigListener.Transformer<T> transformer, ConfigValueListener<Optional<T>> listener) {
        return onOptionalPrefixedValue(
                prefix,
                config -> listener.apply(config.isPresent() ? Optional.of(transformer.apply(config.get())) : Optional.empty())
        );
    }

    public <T> ConfigObserver onPrefixedValue(String prefix, ConfigListener.Transformer<T> transformer, ConfigValueListener<T> listener) {
        return onOptionalPrefixedValue(prefix, config -> onOptionalPrefixedValue(
                prefix,
                transformer,
                opt -> listener.apply(opt.orElseThrow(() -> new ConfigException("Missing required " + prefix)))
        ));
    }

    public ConfigObserver onOptionalPrefixedValue(String prefix, ConfigValueListener<Optional<Map<String, String>>> listener) {
        return onConfigChange(new ConfigListener() {
            @Override
            public void onConfigChanged(Set<String> changedKeys, ConfigMap newConfiguration) throws Exception {
                if (changeIncludes(changedKeys, prefix)) {
                    Optional<Map<String, String>> configuration = newConfiguration.subMap(prefix).map(Function.identity());
                    logger.debug("onConfigChanged key={} value={}", prefix, configuration);
                    listener.apply(configuration);
                }
            }
        });
    }

    private static List<String> parseStringList(String value) {
        return Stream.of(value.split(",")).map(String::trim).collect(Collectors.toList());
    }

    protected void handleFileChanged(List<String> changedFiles) {
        updateConfiguration(configLoader.loadConfiguration());
    }

    public void updateConfiguration(Map<String, String> newConfiguration) {
        logger.trace("New configuration {}", newConfiguration);
        Set<String> changedKeys = findChangedKeys(newConfiguration, currentConfiguration);
        this.currentConfiguration = newConfiguration;
        handleConfigurationChanged(changedKeys, new ConfigMap(newConfiguration));
    }

    private Set<String> findChangedKeys(Map<String, String> newConfiguration, Map<String, String> currentConfiguration) {
        Set<String> changedKeys = new HashSet<>();
        changedKeys.addAll(newConfiguration.keySet());
        changedKeys.addAll(currentConfiguration.keySet());

        changedKeys.removeIf(key -> Objects.equals(newConfiguration.get(key), this.currentConfiguration.get(key)));
        return changedKeys;
    }

    protected void handleConfigurationChanged(Set<String> changedKeys, ConfigMap newConfiguration) {
        for (ConfigListener listener : listeners) {
            notifyListener(listener, changedKeys, newConfiguration);
        }
    }

    private void notifyListener(ConfigListener listener, Set<String> changedKeys, ConfigMap newConfiguration) {
        try {
            logger.trace("Notifying listener {}", listener);
            listener.onConfigChanged(changedKeys, newConfiguration);
        } catch (Exception e) {
            logger.error("Failed to notify listener {} while reloading {}", listener, configLoader.describe(), e);
        }
    }
}

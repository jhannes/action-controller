package org.actioncontroller.config;

import org.assertj.core.annotations.NonNull;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigMap extends AbstractMap<String, String> {
    private final String prefix;
    private final Map<String, String> innerMap;

    public static ConfigMap read(File file) throws IOException {
        Properties properties = new Properties();
        try (FileReader reader = new FileReader(file)) {
            properties.load(reader);
        }
        return new ConfigMap(properties);
    }

    public ConfigMap(String prefix, Map<String, String> innerMap) {
        if (innerMap instanceof ConfigMap) {
            ConfigMap configMap = (ConfigMap) innerMap;
            this.prefix = configMap.prefix + prefix + ".";
            if (!configMap.listSubMaps().contains(prefix)) {
                throw new ConfigException("Missing key " + configMap.prefix + prefix);
            }
            this.innerMap = configMap.innerMap;
        } else {
            this.prefix = prefix + ".";
            this.innerMap = innerMap;
        }
    }

    public ConfigMap(Map<String, String> innerMap) {
        this.prefix = "";
        if (innerMap instanceof ConfigMap) {
            this.innerMap = ((ConfigMap) innerMap).innerMap;
        } else {
            this.innerMap = innerMap;
        }
    }

    public ConfigMap(Properties properties) {
        this.prefix = "";
        this.innerMap = new HashMap<>();
        properties.forEach((key, value) -> innerMap.put(key.toString(), value.toString()));
    }

    public Optional<String> optional(Object key) {
        return Optional.ofNullable(innerMap.get(getInnerKey(key))).map(String::trim).filter(s -> !s.isEmpty());
    }

    /**
     * Returns the value of prefix.key or throws {@link ConfigException} if missing
     * Use getOrDefault with defaultValue null to support optional values
     */
    @Override
    public String get(Object key) {
        return optional(key).orElseThrow(() -> new ConfigException("Missing config value " + getInnerKey(key)));
    }

    /**
     * Returns the value of prefix.key or throws {@link ConfigException} if missing
     */
    @Override
    public String getOrDefault(Object key, String defaultValue) {
        return optional(key).orElse(defaultValue);
    }

    public boolean getBoolean(String key) {
        return getOrDefault(key, "false").equalsIgnoreCase("true");
    }

    @Override
    @NonNull
    public Set<Entry<String, String>> entrySet() {
        Map<String, String> entries = new HashMap<>();
        innerMap.forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                entries.put(key.substring(prefix.length()), value);
            }
        });
        return entries.entrySet();
    }

    public Collection<String> listSubMaps() {
        return keySet().stream().map(s -> s.split("\\.")[0]).collect(Collectors.toSet());
    }

    public ConfigMap getRoot() {
        return new ConfigMap(this.innerMap);
    }

    public Optional<ConfigMap> subMap(String prefix) {
        return listSubMaps().contains(prefix) ? Optional.of(new ConfigMap(prefix, this)) : Optional.empty();
    }

    protected String getInnerKey(Object key) {
        return prefix + key;
    }

    @Override
    public String toString() {
        String values = entrySet().stream().map(this::toString).collect(Collectors.joining(", "));
        return "ConfigMap{prefix=" + prefix + ", values={" + values + "}}";
    }

    private String toString(Entry<String, String> entry) {
        if (entry.getKey().toLowerCase().contains("password") || entry.getKey().toLowerCase().contains("secret")) {
            return entry.getKey() + "=*****";
        }
        return entry.getKey() + "=" + entry.getValue();
    }
}
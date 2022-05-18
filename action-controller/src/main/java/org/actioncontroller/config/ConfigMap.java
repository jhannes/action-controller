package org.actioncontroller.config;

import org.actioncontroller.util.ExceptionUtil;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Period;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Extracts config values from an inner map or environment variables in a
 * hierarchical fashion. For example
 * <code>new ConfigMap(map).subMap("foo").get("bar")</code> will either
 * return <code>map.get("foo.bar")</code>, <code>System.getenv("FOO_BAR")</code>,
 * or throw {@link ConfigException}.
 *
 * <p>The most important operations:</p>
 *
 * <ul>
 *     <li>{@link #get(Object)} returns the prefixed variable specified or
 *     environment variable with a comparable name or else throws exception</li>
 *     <li>{@link #getOrDefault(Object, String)} returns the prefixed variable
 *     specified or environment variable with a comparable name or default value
 *     </li>
 *     <li>{@link #subMap(String)} returns a {@link ConfigMap} which resolves
 *     all variables relative to the prefix</li>
 *     <li>{@link #entrySet()} gets all entries with a key with the current prefix
 *     <strong>does not include environment variables</strong></li>
 * </ul>
 */
public class ConfigMap extends AbstractMap<String, String> {
    private final FileListener observer;
    private final String prefix;
    private final Map<String, String> innerMap;
    private final Map<String, String> environment;
    private final Set<String> touchedProperties = new HashSet<>();

    public static ConfigMap read(FileListener observer, File file) throws IOException {
        Properties properties = new Properties();
        try (FileReader reader = new FileReader(file)) {
            properties.load(reader);
        }
        return new ConfigMap(observer, properties);
    }

    public ConfigMap(FileListener observer, String prefix, Map<String, String> innerMap) {
        this(observer, prefix, innerMap, System.getenv());
    }

    public ConfigMap(FileListener observer, String prefix, Map<String, String> innerMap, Map<String, String> environment) {
        this.observer = Objects.requireNonNull(observer);
        this.environment = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        this.environment.putAll(environment);
        if (innerMap instanceof ConfigMap) {
            ConfigMap configMap = (ConfigMap) innerMap;
            this.prefix = configMap.prefix + prefix + ".";
            if (!configMap.hasPropertiesPrefix(prefix) && !hasEnvironmentPrefix(configMap.prefix + prefix)) {
                throw new ConfigException("Missing key " + configMap.prefix + prefix);
            }
            this.innerMap = configMap.innerMap;
        } else {
            this.prefix = prefix + ".";
            this.innerMap = innerMap;
        }
    }

    public ConfigMap(FileListener observer, Map<String, String> innerMap) {
        this.observer = Objects.requireNonNull(observer);
        this.prefix = "";
        if (innerMap instanceof ConfigMap) {
            this.innerMap = ((ConfigMap) innerMap).innerMap;
        } else {
            this.innerMap = innerMap;
        }
        environment = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        environment.putAll(System.getenv());
    }

    public ConfigMap(FileListener observer) {
        this(observer, new HashMap<>());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public ConfigMap(FileListener observer, Properties properties) {
        this(observer, (Map) properties);
    }

    public Optional<String> optional(Object key) {
        touchedProperties.add(key.toString());
        return Optional.ofNullable(innerMap.get(getPrefixedKey(key))).map(String::trim).filter(s -> !s.isEmpty())
                .or(() -> Optional.ofNullable(environment.get(getEnvironmentKey(getPrefixedKey(key)))));
    }

    private String getEnvironmentKey(String innerKey) {
        return innerKey.replace('.', '_').toUpperCase();
    }

    /**
     * Returns the value of prefix.key or throws {@link ConfigException} if missing
     * Use getOrDefault with defaultValue null to support optional values
     */
    @Override
    public String get(Object key) {
        return optional(key).orElseThrow(() -> new ConfigException("Missing config value " + getPrefixedKey(key)));
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
    public Set<Entry<String, String>> entrySet() {
        Map<String, String> entries = new HashMap<>();
        innerMap.forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                entries.put(key.substring(prefix.length()), value);
            }
        });
        return entries.entrySet();
    }

    public Collection<String> listDirectProperties() {
        return keySet().stream().map(s -> s.split("\\.")[0]).collect(Collectors.toSet());
    }

    public ConfigMap getRoot() {
        return new ConfigMap(observer, this.innerMap);
    }

    public Optional<ConfigMap> subMap(String prefix) {
        touchedProperties.add(prefix);
        return hasPropertiesPrefix(prefix) || hasEnvironmentPrefix(this.prefix + prefix) ? Optional.of(new ConfigMap(observer, prefix, this)) : Optional.empty();
    }

    private boolean hasPropertiesPrefix(String prefix) {
        return keySet().stream().anyMatch(s -> s.startsWith(prefix));
    }

    private boolean hasEnvironmentPrefix(String prefix) {
        return environment.entrySet().stream()
                .anyMatch(entry -> entry.getKey().toUpperCase().startsWith(getEnvironmentKey(prefix) + "_") && !entry.getValue().isEmpty());
    }

    protected String getPrefixedKey(Object key) {
        return prefix + key;
    }

    @Override
    public String toString() {
        return "ConfigMap{prefix=" + prefix + ", values={" + propertiesToString() + "}, env={" + environmentToString() + "}}";
    }

    private String environmentToString() {
        return environment.entrySet().stream()
                .filter(e -> e.getKey().toUpperCase().startsWith(getEnvironmentKey(prefix)))
                .map(entry -> toString(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", "));
    }

    private String propertiesToString() {
        return innerMap.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .map(entry -> toString(entry.getKey().substring(prefix.length()), entry.getValue()))
                .collect(Collectors.joining(", "));
    }

    private String toString(String key, String value) {
        if (key.toLowerCase().contains("password") || key.toLowerCase().contains("secret")) {
            return key + "=*****";
        }
        return key + "=" + value;
    }

    public Iterable<String> getUntouchedProperties() {
        Collection<String> result = new HashSet<>(listDirectProperties());
        result.removeAll(touchedProperties);
        return result;
    }

    public InetSocketAddress getInetSocketAddress(String key, int defaultPort) {
        return optional(key).map(ConfigListener::asInetSocketAddress).orElse(InetSocketAddress.createUnresolved("localhost", defaultPort));
    }

    public Duration getDuration(String key, Duration defaultValue) {
        return optional(key).map(Duration::parse).orElse(defaultValue);
    }

    public Period getPeriod(String key, Period defaultValue) {
        return optional(key).map(Period::parse).orElse(defaultValue);
    }

    public <T> Optional<T> mapOptionalFile(String key, ConfigValueTransformer<Path, T> transformer) throws Exception {
        Optional<Path> path = optionalFile(key);
        if (path.isPresent()) {
            return Optional.ofNullable(transformer.apply(path.get()));
        } else {
            return Optional.empty();
        }
    }

    public Path getRegularFile(String key) {
        Path file = getFile(key);
        if (!Files.isRegularFile(file)) {
            throw new ConfigException("Not a regular file: " + key + "=" + file);
        }
        return file;
    }

    public Path getFile(String key) {
        Path file = Paths.get(get(key));
        if (!Files.exists(file)) {
            throw new ConfigException("File not found: " + key + "=" + file);
        }
        return file;
    }

    public Optional<Path> optionalFile(String key) {
        Optional<String> value = optional(key);
        value.map(Paths::get).ifPresent(path -> listenToFileChange(key, path));
        return value.map(Paths::get).filter(Files::exists);
    }

    public Optional<Path> getFile(String key, String defaultValue) {
        Path value = Paths.get(getOrDefault(key, defaultValue));
        listenToFileChange(key, value);
        return Optional.of(value).filter(Files::exists);
    }

    public void listenToFileChange(String key, Path path) {
        observer.listenToFileChange(
                getPrefixedKey(key),
                path.getParent(),
                f -> f.getFileName().equals(path.getFileName())
        );
    }

    public List<Path> listFiles(String key) {
        return optional(key)
                .map(s -> listFilesByPattern(s, key))
                .orElseGet(List::of);
    }

    public List<Path> listFiles(String key, String defaultPath) {
        return listFilesByPattern(getOrDefault(key, defaultPath), key);
    }

    private List<Path> listFilesByPattern(String pattern, String key) {
        File file = new File(pattern);
        Path parent = Optional
                .ofNullable(file.getParentFile()).map(File::toPath)
                .orElse(Paths.get("."));
        PathMatcher pathMatcher = parent.getFileSystem().getPathMatcher("glob:" + file.getName());
        return listFiles(key, parent, pathMatcher);
    }

    public List<Path> listFiles(String key, Path directory, PathMatcher pathMatcher) {
        observer.listenToFileChange(getPrefixedKey(key), directory, pathMatcher::matches);
        try {
            List<Path> result = new ArrayList<>();
            if (Files.isDirectory(directory)) {
                Files.newDirectoryStream(directory, path -> pathMatcher.matches(path.getFileName())).forEach(result::add);
            }
            return result;
        } catch (IOException e) {
            throw ExceptionUtil.softenException(e);
        }
    }
}

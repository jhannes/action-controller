package org.actioncontroller.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractPrefixConfigListener implements ConfigListener {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    protected final String prefix;

    public AbstractPrefixConfigListener(String prefix) {
        this.prefix = prefix;
    }

    protected boolean containsPrefix(Set<String> changedKeys) {
        return changedKeys.stream().anyMatch(key -> key.startsWith(prefix));
    }

    public void onConfigChanged(Set<String> changedKeys, Map<String, String> config) throws Exception {
        if (changedKeys == null || containsPrefix(changedKeys)) {
            config = filterConfig(config);
            logger.debug("onConfigChanged prefix={} config={}", prefix, config);
            handleConfigChanged(config);
        }
    }

    protected Map<String, String> filterConfig(Map<String, String> config) {
        return config.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    protected abstract void handleConfigChanged(Map<String, String> config) throws Exception;

    protected String required(Map<String, String> config, String key) {
        if (!config.containsKey(key)) {
            throw new IllegalArgumentException("Missing key <" + key + "> in " + config.keySet());
        }
        return config.get(key);
    }

    protected boolean getBoolean(Map<String, String> config, String key) {
        return config.getOrDefault(key, "false").equalsIgnoreCase("true");
    }
}

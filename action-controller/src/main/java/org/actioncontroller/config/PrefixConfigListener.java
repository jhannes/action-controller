package org.actioncontroller.config;

import java.util.Map;
import java.util.Set;

public abstract class PrefixConfigListener<T> implements ConfigListener {
    protected final ConfigValueListener<T> listener;
    protected String prefix;

    public PrefixConfigListener(String prefix, ConfigValueListener<T> listener) {
        this.prefix = prefix;
        this.listener = listener;
    }

    @Override
    public void onConfigChanged(Set<String> changedKeys, Map<String, String> config) throws Exception {
        if (changedKeys == null || containsPrefix(changedKeys)) {
            listener.apply(transform(config));
        }
    }

    protected abstract T transform(Map<String, String> config) throws Exception;

    private boolean containsPrefix(Set<String> changedKeys) {
        return changedKeys.stream().anyMatch(key -> key.startsWith(prefix));
    }
}

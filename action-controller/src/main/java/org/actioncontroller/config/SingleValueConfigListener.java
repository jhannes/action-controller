package org.actioncontroller.config;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class SingleValueConfigListener<T> implements ConfigListener {
    protected final String key;
    protected final ConfigValueListener<T> listener;
    private final T defaultValue;
    private Function<String, T> transformer;

    public SingleValueConfigListener(String key, ConfigValueListener<T> listener, Function<String, T> transformer) {
        this.key = key;
        this.listener = listener;
        this.defaultValue = null;
        this.transformer = transformer;
    }

    public SingleValueConfigListener(String key, ConfigValueListener<T> listener, T defaultValue, Function<String, T> transformer) {
        this.key = key;
        this.listener = listener;
        this.defaultValue = defaultValue;
        this.transformer = transformer;
    }

    @Override
    public final void onConfigChanged(Set<String> changedKeys, Map<String, String> newConfiguration) throws Exception {
        if (changedKeys == null || changedKeys.contains(key)) {
            T configValue = Optional.ofNullable(newConfiguration.get(key))
                    .map(this::transform)
                    .orElseGet(this::getDefaultValue);
            listener.apply(configValue);
        }
    }

    protected T transform(String s) {
        return transformer.apply(s);
    }

    protected T getDefaultValue() {
        return defaultValue;
    }
}

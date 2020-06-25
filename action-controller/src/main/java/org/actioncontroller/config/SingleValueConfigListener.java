package org.actioncontroller.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class SingleValueConfigListener<T> implements ConfigListener {
    private static final Logger logger = LoggerFactory.getLogger(SingleValueConfigListener.class);
    protected final String key;
    protected final ConfigValueListener<T> listener;
    private final T defaultValue;
    private final Function<String, T> transformer;

    public SingleValueConfigListener(String key, ConfigValueListener<T> listener, T defaultValue, Function<String, T> transformer) {
        this.key = key;
        this.listener = listener;
        this.defaultValue = defaultValue;
        this.transformer = transformer;
    }

    @Override
    public final void onConfigChanged(Set<String> changedKeys, ConfigMap newConfiguration) throws Exception {
        if (changedKeys == null || changedKeys.contains(key)) {
            T configValue = Optional.ofNullable(newConfiguration.getOrDefault(key, null))
                    .map(this::transform)
                    .orElseGet(this::getDefaultValue);
            logger.debug("onConfigChanged key={} value={}", key, configValue);
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

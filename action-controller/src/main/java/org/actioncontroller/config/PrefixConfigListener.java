package org.actioncontroller.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class PrefixConfigListener<T> extends AbstractPrefixConfigListener {
    protected final ConfigValueListener<T> listener;

    public PrefixConfigListener(String prefix, ConfigValueListener<T> listener) {
        super(prefix);
        this.listener = listener;
    }

    @Override
    protected void handleConfigChanged(Map<String, String> config) throws Exception {
        listener.apply(transform(config));
    }

    protected abstract T transform(Map<String, String> config) throws Exception;
}

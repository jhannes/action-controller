package org.actioncontroller.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public abstract class PrefixConfigListener<T> implements ConfigListener {
    private static final Logger logger = LoggerFactory.getLogger(PrefixConfigListener.class);

    protected final String prefix;
    protected final ConfigValueListener<T> listener;

    public PrefixConfigListener(String prefix, ConfigValueListener<T> listener) {
        this.prefix = prefix;
        this.listener = listener;
    }

    public void onConfigChanged(Set<String> changedKeys, ConfigMap config) throws Exception {
        if (changeIncludes(changedKeys, prefix)) {
            logger.debug("onConfigChanged prefix={} config={}", prefix, config);
            listener.apply(transform(config));
        }
    }

    protected abstract T transform(ConfigMap config) throws Exception;
}

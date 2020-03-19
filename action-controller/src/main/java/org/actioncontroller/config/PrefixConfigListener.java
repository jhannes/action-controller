package org.actioncontroller.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class PrefixConfigListener<T> implements ConfigListener {
    private static final Logger logger = LoggerFactory.getLogger(PrefixConfigListener.class);
    protected final ConfigValueListener<T> listener;
    protected String prefix;

    public PrefixConfigListener(String prefix, ConfigValueListener<T> listener) {
        this.prefix = prefix;
        this.listener = listener;
    }

    @Override
    public void onConfigChanged(Set<String> changedKeys, Map<String, String> config) throws Exception {
        if (changedKeys == null || containsPrefix(changedKeys)) {
            if (logger.isDebugEnabled()) {
                logger.debug("onConfigChanged prefix={} config={}",
                        prefix,
                        config.entrySet().stream()
                                .filter(e -> e.getKey().startsWith(prefix))
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            }
            listener.apply(transform(config));
        }
    }

    protected abstract T transform(Map<String, String> config) throws Exception;

    private boolean containsPrefix(Set<String> changedKeys) {
        return changedKeys.stream().anyMatch(key -> key.startsWith(prefix));
    }
}

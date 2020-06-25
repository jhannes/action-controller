package org.actioncontroller.config;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;

/**
 * Listener interface. Use this to determine what to do with the configuration. Is invoked
 * with the full configuration.
 */
@FunctionalInterface
public interface ConfigListener {
    @FunctionalInterface
    interface Transformer<T> {
        T apply(Map<String, String> configuration) throws Exception;
    }

    void onConfigChanged(Set<String> changedKeys, ConfigMap newConfiguration) throws Exception;

    default boolean changeIncludes(Set<String> changedKeys, String prefix) {
        return changedKeys == null || changedKeys.stream().anyMatch(key -> key.startsWith(prefix));
    }

    static InetSocketAddress asInetSocketAddress(String value) {
        int colonPos = value.indexOf(':');
        if (colonPos < 0) {
            return new InetSocketAddress(Integer.parseInt(value));
        } else if (colonPos == 0) {
            return new InetSocketAddress(Integer.parseInt(value.substring(1)));
        } else {
            return new InetSocketAddress(
                    value.substring(0, colonPos),
                    Integer.parseInt(value.substring(colonPos+1))
            );
        }
    }

}

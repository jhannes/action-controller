package org.actioncontroller.config;

import java.util.Map;
import java.util.Set;

/**
 * Listener interface. Use this to determine what to do with the configuration. Is invoked
 * with the full configuration.
 */
@FunctionalInterface
public interface ConfigListener {
    void onConfigChanged(Set<String> changedKeys, Map<String, String> newConfiguration) throws Exception;
}

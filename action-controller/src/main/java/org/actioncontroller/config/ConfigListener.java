package org.actioncontroller.config;

import java.util.Map;
import java.util.Set;

@FunctionalInterface
public interface ConfigListener {
    void onConfigChanged(Set<String> changedKeys, Map<String, String> newConfiguration) throws Exception;
}

package org.actioncontroller.config;

import java.util.Map;
import java.util.function.Consumer;

public interface ConfigLoader {
    Map<String, String> loadConfiguration();

    void watch(Consumer<Map<String, String>> configChangeListener);

    String describe();
}

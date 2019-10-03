package org.actioncontroller.config;

/**
 * Listener interface. Use this to determine what to do with the configuration. Is invoked
 * with a single configuration value.
 */
@FunctionalInterface
public interface ConfigValueListener<T> {
    void apply(T value) throws Exception;
}

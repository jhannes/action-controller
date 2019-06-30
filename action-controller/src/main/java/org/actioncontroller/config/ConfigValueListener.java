package org.actioncontroller.config;

@FunctionalInterface
public interface ConfigValueListener<T> {
    void apply(T value) throws Exception;
}

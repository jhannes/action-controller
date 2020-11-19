package org.actioncontroller.config;

@FunctionalInterface
public interface ConfigValueTransformer<T> {
    T apply(String value) throws Exception;
}

package org.actioncontrollerdemo.config;

@FunctionalInterface
public interface ConfigurationConsumer<T> {
    void accept(T t) throws Exception;

}

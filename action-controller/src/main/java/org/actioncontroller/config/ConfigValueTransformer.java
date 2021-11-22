package org.actioncontroller.config;

@FunctionalInterface
public interface ConfigValueTransformer<FROM, TO> {
    TO apply(FROM value) throws Exception;
}

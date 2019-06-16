package org.actioncontrollerdemo.config;

public class ApplicationConfigurationException extends RuntimeException {
    public ApplicationConfigurationException(String message, Exception cause) {
        super(message, cause);
    }
}

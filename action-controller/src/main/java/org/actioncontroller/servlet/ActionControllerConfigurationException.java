package org.actioncontroller.servlet;

public class ActionControllerConfigurationException extends RuntimeException {
    public ActionControllerConfigurationException(String message) {
        super(message);
    }

    public ActionControllerConfigurationException(String message, Exception e) {
        super(message, e);
    }
}


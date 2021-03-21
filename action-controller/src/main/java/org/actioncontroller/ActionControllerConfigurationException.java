package org.actioncontroller;

/**
 * Thrown when something goes wrong during setup of a controller or the corresponding mapping.
 */
public class ActionControllerConfigurationException extends RuntimeException {
    public ActionControllerConfigurationException(String message) {
        super(message);
    }

    public ActionControllerConfigurationException(String message, Exception e) {
        super(message, e);
    }
}


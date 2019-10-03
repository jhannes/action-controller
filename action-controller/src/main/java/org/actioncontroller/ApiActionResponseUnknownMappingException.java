package org.actioncontroller;

import org.actioncontroller.servlet.ActionControllerConfigurationException;

import java.lang.reflect.Method;

/**
 * Thrown when action-controller couldn't find out how to map the return value
 * for an action method.
 */
public class ApiActionResponseUnknownMappingException extends ActionControllerConfigurationException {

    public ApiActionResponseUnknownMappingException(Method action, Class<?> returnType) {
        this("No mapping annotation for " + action.getName() + "() return type of type " + returnType);
    }

    public ApiActionResponseUnknownMappingException(String message) {
        super(message);
    }

}

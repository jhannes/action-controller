package org.actioncontroller.exceptions;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * Thrown when action-controller couldn't find out how to map the return value
 * for an action method.
 */
public class ApiActionResponseUnknownMappingException extends ActionControllerConfigurationException {

    public ApiActionResponseUnknownMappingException(Method action, Type returnType) {
        this("No mapping annotation for " + action.getName() + "() return type of type " + returnType);
    }

    public ApiActionResponseUnknownMappingException(String message) {
        super(message);
    }

}

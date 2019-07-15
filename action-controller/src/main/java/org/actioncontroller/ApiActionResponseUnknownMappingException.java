package org.actioncontroller;

import org.actioncontroller.servlet.ApiServletException;

import java.lang.reflect.Method;

public class ApiActionResponseUnknownMappingException extends ApiServletException {

    public ApiActionResponseUnknownMappingException(Method action, Class<?> returnType) {
        this("No mapping annotation for " + action.getName() + "() return type of type " + returnType);
    }

    public ApiActionResponseUnknownMappingException(String message) {
        super(message);
    }

}

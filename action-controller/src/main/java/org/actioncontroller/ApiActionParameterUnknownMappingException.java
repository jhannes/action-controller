package org.actioncontroller;

import org.actioncontroller.servlet.ActionControllerConfigurationException;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class ApiActionParameterUnknownMappingException extends ActionControllerConfigurationException {

    public ApiActionParameterUnknownMappingException(Method action, int index, Parameter parameter) {
        this("No mapping annotation for " + action.getName() + "() parameter " + index + " of type " + parameter + additionalInfo(parameter));
    }

    private static String additionalInfo(Parameter parameter) {
        if (parameter.getAnnotations().length == 1) {
            return " (should " + parameter.getAnnotations()[0].annotationType() + " have @HttpParameterMapping?)";
        } else {
            return "";
        }
    }

    public ApiActionParameterUnknownMappingException(String message) {
        super(message);
    }


}

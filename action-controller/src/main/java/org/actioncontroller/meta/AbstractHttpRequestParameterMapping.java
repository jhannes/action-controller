package org.actioncontroller.meta;

import org.actioncontroller.HttpRequestException;

import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.UUID;

public abstract class AbstractHttpRequestParameterMapping implements HttpRequestParameterMapping {

    protected Parameter parameter;

    public AbstractHttpRequestParameterMapping(Parameter parameter) {
        this.parameter = parameter;
    }

    protected static Object convertParameterType(String value, Parameter parameter, Type parameterType) {
        if (parameterType == String.class) {
            return value;
        } else if (parameterType == Boolean.class) {
            return Boolean.parseBoolean(value);
        } else if (parameterType == Integer.class || parameterType == Integer.TYPE) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new HttpRequestException(400,
                        String.format("Invalid parameter amount '%s' is not an %s", value, parameterType));
            }
        } else if (parameter.getType() == UUID.class) {
            return UUID.fromString(value);
        } else if (parameterType == Long.class || parameterType == Long.TYPE) {
            return Long.parseLong(value);
        } else {
            throw new HttpRequestException(500, "Unhandled parameter type " + parameterType);
        }
    }

    protected Object convertToParameterType(String value, String parameterName) {
        return convertTo(value, parameterName, parameter);
    }

    public static Object convertTo(String value, String parameterName, Parameter parameter) {
        boolean optional = parameter.getType() == Optional.class;

        if (value == null) {
            if (!optional) {
                throw new HttpRequestException(400, "Missing required parameter " + parameterName);
            }
            return Optional.empty();
        }

        Type parameterType;
        if (optional) {
            Type parameterizedType = parameter.getParameterizedType();
            parameterType = ((ParameterizedType)parameterizedType).getActualTypeArguments()[0];
        } else {
            parameterType = parameter.getType();
        }

        Object parameterValue = convertParameterType(value, parameter, parameterType);
        return optional ? Optional.of(parameterValue) : parameterValue;
    }

}

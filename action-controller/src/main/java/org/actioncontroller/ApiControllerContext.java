package org.actioncontroller;

import org.actioncontroller.exceptions.ActionControllerConfigurationException;

import java.util.HashMap;
import java.util.Map;

/**
 * Extra initialization variables for the ActionControllers. Attributes can be set on an
 * {@link org.actioncontroller.servlet.ApiServlet} or {@link org.actioncontroller.httpserver.ApiHandler} and
 * read by {@link org.actioncontroller.meta.HttpParameterMapperFactory#create}
 */
public class ApiControllerContext {
    private Map<String, Object> attributes = new HashMap<>();

    public ApiControllerContext setAttribute(String name, Object value) {
        attributes.put(name, value);
        return this;
    }

    public ApiControllerContext setAttribute(Object o) {
        return setAttribute(o.getClass().getName(), o);
    }

    public Object getAttribute(String name) {
        if (!attributes.containsKey(name)) {
            throw new ActionControllerConfigurationException("Missing context parameter, call servlet.getContext().setAttribute(\"" + name + "\", ...)");
        }
        return attributes.get(name);
    }

    public <T> T getAttribute(Class<T> className) {
        return (T) getAttribute(className.getName());
    }
}

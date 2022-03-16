package org.actioncontroller;

import org.actioncontroller.exceptions.ActionControllerConfigurationException;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Extra initialization variables for the ActionControllers. Attributes can be set on an
 * {@link org.actioncontroller.servlet.ApiServlet} or {@link org.actioncontroller.httpserver.ApiHandler} and
 * read by {@link org.actioncontroller.meta.HttpParameterMapperFactory#create}
 */
public class ApiControllerContext {
    private final Map<String, Object> attributes = new HashMap<>();

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

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(Class<T> className) {
        return (T) getAttribute(className.getName());
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(Class<T> className, Supplier<T> creator) {
        if (!attributes.containsKey(className.getName())) {
            return creator.get();
        }
        return (T) attributes.get(className.getName());
    }
}

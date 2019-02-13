package org.actioncontroller;


import org.actioncontroller.json.JsonHttpRequestException;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpRequestParameterMapping;
import org.actioncontroller.meta.HttpResponseValueMapping;
import org.actioncontroller.meta.HttpReturnMapping;
import org.jsonbuddy.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.*;

// TODO Factory for mappings
class ApiServletAction {

    private static Logger logger = LoggerFactory.getLogger(ApiServletAction.class);

    private String pattern;

    private List<HttpRequestParameterMapping> parameterMappers = new ArrayList<>();

    private HttpResponseValueMapping responseMapper;

    public ApiServletAction(Object controller, Method action, String pattern) {
        this.controller = controller;
        this.action = action;
        this.pattern = pattern;

        Parameter[] parameters = action.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            try {
                parameterMappers.add(createParameterMapper(parameters[i]));
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to map parameter " + i  + " of " + action + " of type " + parameters[i].getType() + ": " + e);
            }
        }

        responseMapper = createResponseMapper();
    }

    private static Map<Class<?>, HttpResponseValueMapping> typebasedResponseMapping = new HashMap<>();
    static {
        typebasedResponseMapping.put(URL.class, (o, resp) -> resp.sendRedirect(o.toString()));
    }

    private HttpResponseValueMapping createResponseMapper() {
        if (action.getReturnType() == Void.TYPE) {
            return (a, b) -> {};
        }

        for (Annotation annotation : action.getAnnotations()) {
            HttpReturnMapping mappingAnnotation = annotation.annotationType().getAnnotation(HttpReturnMapping.class);
            if (mappingAnnotation != null) {
                Class<?> value = mappingAnnotation.value();
                try {
                    try {
                        return (HttpResponseValueMapping) value
                                .getDeclaredConstructor(annotation.annotationType(), Class.class)
                                .newInstance(annotation, action.getReturnType());
                    } catch (NoSuchMethodException e) {
                        return (HttpResponseValueMapping) value.getDeclaredConstructor().newInstance();
                    }
                } catch (NoSuchMethodException e) {
                    throw new IllegalArgumentException(value + " must have (" + annotation.annotationType() + ", Class) constructor or no-argument constructor");
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                        | InvocationTargetException | SecurityException e) {
                    throw ExceptionUtil.softenException(e);
                }
            }
        }

        return Optional.ofNullable(typebasedResponseMapping.get(action.getReturnType()))
                .orElseThrow(() -> new IllegalArgumentException("Don't know how to map response for " + action));
    }

    protected HttpRequestParameterMapping createParameterMapper(Parameter parameter) {
        for (Annotation annotation : parameter.getAnnotations()) {
            HttpParameterMapping mappingAnnotation = annotation.annotationType().getAnnotation(HttpParameterMapping.class);
            if (mappingAnnotation != null) {
                Class<?> value = mappingAnnotation.value();
                try {
                    try {
                        return (HttpRequestParameterMapping) value
                                .getDeclaredConstructor(annotation.annotationType(), Parameter.class)
                                .newInstance(annotation, parameter);
                    } catch (NoSuchMethodException e) {
                        return (HttpRequestParameterMapping) value.getDeclaredConstructor().newInstance();
                    }
                } catch (NoSuchMethodException e) {
                    throw ExceptionUtil.softenException(e);
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | SecurityException e) {
                    throw ExceptionUtil.softenException(e);
                } catch (InvocationTargetException e) {
                    throw ExceptionUtil.softenException((Exception) e.getTargetException());
                }
            }
        }

        HttpRequestParameterMapping typeBasedMapping = typebasedRequestMapping.get(parameter.getType());
        if (typeBasedMapping != null) {
            return typeBasedMapping;
        }
        throw new IllegalArgumentException("Cannot find a way to map " + parameter + " of " + action);
    }

    private static Map<Class<?>, HttpRequestParameterMapping> typebasedRequestMapping = new HashMap<>();
    static {
        typebasedRequestMapping.put(HttpSession.class, (req, map) -> req.getSession());
        typebasedRequestMapping.put(HttpServletRequest.class, (req, map) -> req);
    }

    private final Object controller;

    private final Method action;

    public Object getController() {
        return controller;
    }

    public Method getAction() {
        return action;
    }

    public boolean matches(String pathInfo, Map<String, String> pathParameters) {
        String[] patternParts = this.pattern.split("/");
        String[] actualParts = pathInfo.split("/");
        if (patternParts.length != actualParts.length) return false;

        for (int i = 0; i < patternParts.length; i++) {
            if (patternParts[i].startsWith(":")) {
                pathParameters.put(patternParts[i].substring(1), actualParts[i]);
            } else if (!patternParts[i].equals(actualParts[i])) {
                return false;
            }
        }

        return true;
    }

    public void invoke(
            HttpServletRequest req,
            HttpServletResponse resp,
            Map<String, String> pathParameters,
            ApiServlet apiServlet
    ) throws IOException {
        try {
            verifyUserAccess(req, apiServlet);
            Object[] arguments = createArguments(getAction(), req, pathParameters);
            Object result = invoke(getController(), getAction(), arguments);
            responseMapper.accept(result, resp);
        } catch (HttpRequestException e) {
            sendError(e, resp);
        }
    }

    protected void verifyUserAccess(HttpServletRequest req, ApiServlet apiServlet) {
        String role = getRequiredUserRole().orElse(null);
        if (role == null) {
            return;
        }
        if (!apiServlet.isUserLoggedIn(req)) {
            throw new JsonHttpRequestException(401,
                    "User must be logged in for " + action,
                    new JsonObject().put("message", "Login required"));
        }
        if (!apiServlet.isUserInRole(req, role)) {
            throw new JsonHttpRequestException(403,
                    "User failed to authenticate for " + action + ": Missing role " + role + " for user",
                    new JsonObject().put("message", "Insufficient permissions"));
        }
    }

    protected void sendError(HttpRequestException e, HttpServletResponse resp) throws IOException {
        if (e.getStatusCode() >= 500) {
            logger.error("While serving {}", this, e);
        } else {
            logger.info("While serving {}", this, e);
        }
        e.sendError(resp);
    }

    protected Optional<String> getRequiredUserRole() {
        return Optional.ofNullable(this.getAction().getDeclaredAnnotation(RequireUserRole.class)).map(a -> a.value());
    }

    private Object invoke(Object controller, Method action, Object[] arguments) {
        try {
            return action.invoke(controller, arguments);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof RuntimeException) {
                throw (RuntimeException)e.getTargetException();
            } else {
                e.getTargetException().printStackTrace();
                throw new HttpRequestException(500, e.getTargetException());
            }
        } catch (IllegalAccessException e) {
            throw new HttpRequestException(500, e);
        }
    }

    private Object[] createArguments(Method method, HttpServletRequest req, Map<String, String> pathParameters) throws IOException {
        Object[] arguments = new Object[method.getParameterCount()];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = parameterMappers.get(i).apply(req, pathParameters);
        }
        return arguments;
    }
}
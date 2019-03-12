package org.actioncontroller;

import org.actioncontroller.json.JsonHttpRequestException;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpRequestParameterMapping;
import org.actioncontroller.meta.HttpRequestParameterMappingFactory;
import org.actioncontroller.meta.HttpReturnValueMapping;
import org.actioncontroller.meta.HttpReturnMapperFactory;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a single action that can be performed on a controller.
 * An action is defined on the http-end as a http method and a path
 * template, and on the Java-side as a method on the controller class.
 */
class ApiServletAction {

    private final static Logger logger = LoggerFactory.getLogger(ApiServletAction.class);

    private String pattern;

    private List<HttpRequestParameterMapping> parameterMappers = new ArrayList<>();

    private HttpReturnValueMapping responseMapper;

    public ApiServletAction(Object controller, Method action, String pattern) {
        this.controller = controller;
        this.action = action;
        this.pattern = pattern;

        Parameter[] parameters = action.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            parameterMappers.add(createParameterMapper(parameters[i], i));
        }

        responseMapper = createResponseMapper();

        verifyPathParameters();
    }

    private void verifyPathParameters() {
        List<String> specifiedPathParameters = Stream.of(pattern.split("/"))
                .filter(s -> s.startsWith(":"))
                .map(s -> s.substring(1))
                .collect(Collectors.toList());
        List<String> boundPathParameters = Stream.of(action.getParameters())
                .map(p -> p.getAnnotation(PathParam.class))
                .filter(Objects::nonNull)
                .map(PathParam::value)
                .collect(Collectors.toList());

        List<String> extraParameters = new ArrayList<>(boundPathParameters);
        extraParameters.removeAll(specifiedPathParameters);

        List<String> unboundParameters = new ArrayList<>(specifiedPathParameters);
        unboundParameters.removeAll(boundPathParameters);

        if (!unboundParameters.isEmpty()) {
            logger.warn("Unused path parameters for {}: {}", action, unboundParameters);
        }
        if (!extraParameters.isEmpty()) {
            throw new ApiActionParameterUnknownMappingException(
                    "Unknown parameters specified for " + action.getName() + ": " + extraParameters);
        }
    }

    private static Map<Class<?>, HttpReturnValueMapping> typebasedResponseMapping = new HashMap<>();
    static {
        typebasedResponseMapping.put(URL.class, (o, resp, req) -> resp.sendRedirect(o.toString()));
    }

    private HttpReturnValueMapping createResponseMapper() {
        if (action.getReturnType() == Void.TYPE) {
            return (a, b, req) -> {};
        }

        for (Annotation annotation : action.getAnnotations()) {
            HttpReturnMapping mappingAnnotation = annotation.annotationType().getAnnotation(HttpReturnMapping.class);
            if (mappingAnnotation != null) {
                Class<? extends HttpReturnMapperFactory> value = mappingAnnotation.value();
                try {
                    return value
                            .getDeclaredConstructor()
                            .newInstance()
                            .create(annotation, action.getReturnType());
                } catch (NoSuchMethodException e) {
                    throw new ApiActionResponseUnknownMappingException(
                            "No mapping annotation for " + action.getName() + "() return type"
                                    + ": Illegal mapping function for " + value + " (no default constructor)");
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | SecurityException e) {
                    throw ExceptionUtil.softenException(e);
                } catch (InvocationTargetException e) {
                    throw ExceptionUtil.softenException(e.getTargetException());
                }
            }
        }

        return Optional.ofNullable(typebasedResponseMapping.get(action.getReturnType()))
                .orElseThrow(() -> new ApiActionResponseUnknownMappingException(action, action.getReturnType()));
    }

    private HttpRequestParameterMapping createParameterMapper(Parameter parameter, int index) {
        for (Annotation annotation : parameter.getAnnotations()) {
            HttpParameterMapping mappingAnnotation = annotation.annotationType().getAnnotation(HttpParameterMapping.class);
            if (mappingAnnotation != null) {
                Class<? extends HttpRequestParameterMapping> value = mappingAnnotation.value();
                try {
                    try {
                        return value
                                .getDeclaredConstructor(annotation.annotationType(), Parameter.class)
                                .newInstance(annotation, parameter);
                    } catch (NoSuchMethodException e) {
                        return value.getDeclaredConstructor().newInstance();
                    }
                } catch (NoSuchMethodException e) {
                    throw new ApiActionParameterUnknownMappingException(
                            "No mapping annotation for " + action.getName() + "() parameter " + index
                                    + ": Illegal mapping factory " + value + " (no default constructor)");
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | SecurityException e) {
                    throw ExceptionUtil.softenException(e);
                } catch (InvocationTargetException e) {
                    throw ExceptionUtil.softenException(e.getTargetException());
                }
            }
        }

        HttpRequestParameterMapping typeBasedMapping = typebasedRequestMapping.get(parameter.getType());
        if (typeBasedMapping != null) {
            return typeBasedMapping;
        }
        throw new ApiActionParameterUnknownMappingException(action, index, parameter);
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

    public Map<String, String> collectPathParameters(String pathInfo) {
        HashMap<String, String> pathParameters = new HashMap<>();

        String[] patternParts = this.pattern.split("/");
        String[] actualParts = pathInfo.split("/");
        if (patternParts.length != actualParts.length) {
            throw new IllegalArgumentException("Paths don't match <" + pattern + ">, but was <" + pathInfo + ">");
        }

        for (int i = 0; i < patternParts.length; i++) {
            if (patternParts[i].startsWith(":")) {
                pathParameters.put(patternParts[i].substring(1), actualParts[i]);
            } else if (!patternParts[i].equals(actualParts[i])) {
                throw new IllegalArgumentException("Paths don't match <" + pattern + ">, but was <" + pathInfo + ">");
            }
        }

        return pathParameters;
    }

    boolean matches(String pathInfo) {
        String[] patternParts = this.pattern.split("/");
        String[] actualParts = pathInfo.split("/");
        if (patternParts.length != actualParts.length) return false;

        for (int i = 0; i < patternParts.length; i++) {
            if (!patternParts[i].startsWith(":") && !patternParts[i].equals(actualParts[i])) {
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
            responseMapper.accept(result, resp, req);
        } catch (HttpRequestException e) {
            sendError(e, resp);
        }
    }

    // TODO: It feels like there is some more generic concept missing here
    // TODO: Perhaps a mechanism like transaction wrapping could be supported?
    // TODO: Timing logging? MDC boundary?
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
        return Optional.ofNullable(
                this.getAction().getDeclaredAnnotation(RequireUserRole.class)
        ).map(RequireUserRole::value);
    }

    /**
     * Invoke the method on the controller with the given parameters and
     * translate exceptions to http status codes
     */
    protected Object invoke(Object controller, Method action, Object[] arguments) {
        try {
            return action.invoke(controller, arguments);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof RuntimeException) {
                throw (RuntimeException)e.getTargetException();
            } else {
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
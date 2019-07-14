package org.actioncontroller;

import org.actioncontroller.json.JsonHttpActionException;
import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpRequestParameterMapping;
import org.actioncontroller.meta.HttpRequestParameterMappingFactory;
import org.actioncontroller.meta.HttpReturnMapperFactory;
import org.actioncontroller.meta.HttpReturnMapping;
import org.actioncontroller.meta.HttpReturnValueMapping;
import org.jsonbuddy.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * For example <code>@Get("/helloWorld") public String hello(@RequestParam("greeter") String greeter)</code>
 * defines an action that responds to <code>GET /helloWorld?greeter=something</code> with a string.
 *
 * TODO: Rename to ApiControllerAction
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

    static void registerActions(Object controller, Map<String, List<ApiServletAction>> routes) {
        ApiControllerCompositeException exceptions = new ApiControllerCompositeException(controller);
        for (Method method : controller.getClass().getMethods()) {
            try {
                addRoute("GET", Optional.ofNullable(method.getAnnotation(Get.class)).map(Get::value),
                        controller, method, routes);
                addRoute("POST", Optional.ofNullable(method.getAnnotation(Post.class)).map(Post::value),
                        controller, method, routes);
                addRoute("PUT", Optional.ofNullable(method.getAnnotation(Put.class)).map(Put::value),
                        controller, method, routes);
                addRoute("DELETE", Optional.ofNullable(method.getAnnotation(Delete.class)).map(Delete::value),
                        controller, method, routes);
            } catch (ApiServletException e) {
                logger.warn("Failed to setup {}", getMethodName(method), e);
                exceptions.addActionException(e);
            }
        }
        if (!exceptions.isEmpty()) {
            throw exceptions;
        }
    }

    private static void addRoute(String httpMethod, Optional<Object> path, Object controller, Method actionMethod, Map<String, List<ApiServletAction>> routes) {
        path.ifPresent(p -> {
            routes.get(httpMethod).add(new ApiServletAction(controller, actionMethod, p.toString()));
            logger.info("Installing route {} ...{} as {}", httpMethod, p, getMethodName(actionMethod));
        });
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
            logger.warn("Unused path parameters for {}: {}", getMethodName(action), unboundParameters);
        }
        if (!extraParameters.isEmpty()) {
            throw new ApiActionParameterUnknownMappingException(
                    "Unknown parameters specified for " + action.getName() + ": " + extraParameters);
        }
    }

    private static Map<Class<?>, HttpReturnValueMapping> typebasedResponseMapping = new HashMap<>();
    static {
        typebasedResponseMapping.put(URL.class, (o, exchange) -> exchange.sendRedirect(o.toString()));
    }

    private HttpReturnValueMapping createResponseMapper() {
        if (action.getReturnType() == Void.TYPE) {
            return (a, exchange) -> {};
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
                Class<? extends HttpRequestParameterMappingFactory> value = mappingAnnotation.value();
                try {
                    return value.getDeclaredConstructor().newInstance().create(annotation, parameter);
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
        typebasedRequestMapping.put(ApiHttpExchange.class, (exchange) -> exchange);
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
        if (pathInfo == null) {
            return this.pattern.isEmpty();
        }
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

    public void invoke(UserContext userContext, ApiHttpExchange exchange) throws IOException {
        verifyUserAccess(exchange, userContext);
        Object[] arguments = createArguments(getAction(), exchange);
        Object result = invoke(getController(), getAction(), arguments);
        responseMapper.accept(result, exchange);
    }

    // TODO: It feels like there is some more generic concept missing here
    // TODO: Perhaps a mechanism like transaction wrapping could be supported?
    // TODO: Timing logging? MDC boundary?
    protected void verifyUserAccess(ApiHttpExchange exchange, UserContext userContext) {
        String role = getRequiredUserRole().orElse(null);
        if (role == null) {
            return;
        }
        if (!userContext.isUserLoggedIn(exchange)) {
            throw new JsonHttpActionException(401,
                    "User must be logged in for " + action,
                    new JsonObject().put("message", "Login required"));
        }
        if (!userContext.isUserInRole(exchange, role)) {
            throw new JsonHttpActionException(403,
                    "User failed to authenticate for " + action + ": Missing role " + role + " for user",
                    new JsonObject().put("message", "Insufficient permissions"));
        }
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
            if (e.getTargetException() instanceof HttpActionException) {
                throw (HttpActionException)e.getTargetException();
            } else {
                logger.error("While invoking {}", getMethodName(action), e.getTargetException());
                throw new HttpServerErrorException(e.getTargetException());
            }
        } catch (IllegalAccessException e) {
            logger.error("While invoking {}", getMethodName(action), e);
            throw new HttpServerErrorException(e);
        }
    }

    private static Object getMethodName(Method action) {
        String parameters = Stream.of(action.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(","));
        return action.getDeclaringClass().getSimpleName() + "." + action.getName() + "(" + parameters + ")";
    }

    private Object[] createArguments(Method method, ApiHttpExchange exchange) throws IOException {
        try {
            Object[] arguments = new Object[method.getParameterCount()];
            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = parameterMappers.get(i).apply(exchange);
            }
            return arguments;
        } catch (HttpActionException e) {
            StackTraceElement[] stackTrace = e.getStackTrace();
            StackTraceElement[] replacedStackTrace = new StackTraceElement[stackTrace.length+1];
            replacedStackTrace[0] = new StackTraceElement(method.getDeclaringClass().getName(), method.getName(),
                    method.getDeclaringClass().getSimpleName() + ".java", 1);
            System.arraycopy(stackTrace, 0, replacedStackTrace, 1, stackTrace.length);
            e.setStackTrace(replacedStackTrace);

            throw e;
        } catch (RuntimeException e) {
            logger.warn("While processing {} arguments", exchange, e);
            throw new HttpRequestException(e);
        }
    }
}

package org.actioncontroller;

import org.actioncontroller.json.JsonHttpActionException;
import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpReturnMapper;
import org.actioncontroller.meta.HttpReturnMapperFactory;
import org.jsonbuddy.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a single action that can be performed on a controller.
 * An action is defined on the http-end as a http method and a path
 * template, and on the Java-side as a method on the controller class.
 * For example <code>@Get("/helloWorld") public String hello(@RequestParam("greeter") String greeter)</code>
 * defines an action that responds to <code>GET /helloWorld?greeter=something</code> with a string.
 */
public class ApiControllerMethodAction implements ApiControllerAction {

    private final static Logger logger = LoggerFactory.getLogger(ApiControllerAction.class);
    private final Optional<String> requiredParameter;
    public static final Pattern PATH_PARAM_PATTERN = Pattern.compile("^(:(.\\w*)|^\\{(\\w*)})(\\.(\\w+))?$");

    private final String httpMethod;
    private final Object controller;
    private final Method action;
    private final String pattern;
    private final String[] patternParts;
    private final String[] pathParams;
    private final Pattern[] paramRegexp;

    private final List<HttpParameterMapper> parameterMappers = new ArrayList<>();

    private final HttpReturnMapper responseMapper;

    public ApiControllerMethodAction(String httpMethod, String pattern, Object controller, Method action, ApiControllerContext context) {
        this.httpMethod = httpMethod;
        this.controller = controller;
        this.action = action;
        this.pattern = pattern;
        this.patternParts = getPatternParts(pattern);
        this.requiredParameter = getRequiredParameter(pattern);
        this.pathParams = new String[patternParts.length];
        this.paramRegexp = new Pattern[patternParts.length];

        for (int i = 0; i < action.getParameters().length; i++) {
            parameterMappers.add(createParameterMapper(i, context));
        }

        for (int i = 0; i < patternParts.length; i++) {
            String patternPart = patternParts[i];
            Matcher matcher = PATH_PARAM_PATTERN.matcher(patternPart);
            if (matcher.matches()) {
                pathParams[i] = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
                if (matcher.group(5) != null && !matcher.group(5).isEmpty()) {
                    paramRegexp[i] = Pattern.compile("^(.+)\\." + matcher.group(5) + "$");
                } else {
                    paramRegexp[i] = Pattern.compile("^(.*)$");
                }
            }
        }

        responseMapper = createResponseMapper();
        verifyPathParameters();
    }

    private static String[] getPatternParts(String pattern) {
        return pattern.indexOf('?') > 0 ? pattern.substring(0, pattern.indexOf('?')).split("/") : pattern.split("/");
    }

    private static Optional<String> getRequiredParameter(String pattern) {
        return pattern.indexOf('?') > 0 ? Optional.of(pattern.substring(pattern.indexOf('?') + 1)) : Optional.empty();
    }

    private void verifyPathParameters() {
        List<String> specifiedPathParameters = Stream.of(pathParams)
                .filter(Objects::nonNull)
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
            logger.warn("Unused path parameters for {}: {}", getMethodName(), unboundParameters);
        }
        if (!extraParameters.isEmpty()) {
            throw new ApiActionParameterUnknownMappingException(
                    "Unknown parameters parameters specified for " + pattern + ": " + extraParameters + " not in " + specifiedPathParameters);
        }
    }

    private static final Map<Class<?>, HttpReturnMapper> typebasedResponseMapping = new HashMap<>();
    static {
        typebasedResponseMapping.put(URL.class, (o, exchange) -> exchange.sendRedirect(o.toString()));
        typebasedResponseMapping.put(Void.TYPE, (o, exchange) -> {});
    }

    private static final Map<Class<?>, HttpParameterMapper> typebasedRequestMapping = new HashMap<>();
    static {
        typebasedRequestMapping.put(ApiHttpExchange.class, (exchange) -> exchange);
    }

    private HttpReturnMapper createResponseMapper() {
        return HttpReturnMapperFactory.createNewInstance(action)
                .or(() -> Optional.ofNullable(typebasedResponseMapping.get(action.getReturnType())))
                .orElseThrow(() -> new ApiActionResponseUnknownMappingException(action, action.getReturnType()));
    }

    private HttpParameterMapper createParameterMapper(int index, ApiControllerContext context) {
        return HttpParameterMapperFactory.createNewInstance(action.getParameters()[index], context)
                .or(() -> Optional.ofNullable(typebasedRequestMapping.get(action.getParameters()[index].getType())))
                .orElseThrow(() -> new ApiActionParameterUnknownMappingException(action, index));
    }

    @Override
    public Object getController() {
        return controller;
    }

    @Override
    public Method getAction() {
        return action;
    }

    @Override
    public String getPattern() {
        return pattern;
    }

    @Override
    public String getHttpMethod() {
        return httpMethod;
    }

    @Override
    public boolean requiresParameter() {
        return requiredParameter.isPresent();
    }

    @Override
    public boolean matchesRequiredParameters(ApiHttpExchange exchange) {
        return requiredParameter.map(exchange::hasParameter).orElse(true);
    }

    @Override
    public String[] getPatternParts() {
        return patternParts;
    }

    @Override
    public Pattern[] getParamRegexp() {
        return paramRegexp;
    }

    @Override
    public boolean matches(ApiControllerAction otherAction) {
        if (!getHttpMethod().equals(otherAction.getHttpMethod())) {
            return false;
        }

        if (!getRequiredParameter(otherAction.getPattern()).equals(getRequiredParameter(pattern))) {
            return false;
        }

        String[] otherPatternParts = getPatternParts(otherAction.getPattern());
        if (otherPatternParts.length != this.patternParts.length) {
            return false;
        }
        for (int i = 0; i < patternParts.length; i++) {
            Matcher matcher = PATH_PARAM_PATTERN.matcher(patternParts[i]);
            Matcher otherMatcher = PATH_PARAM_PATTERN.matcher(otherPatternParts[i]);

            if (matcher.matches() || otherMatcher.matches()) {
                if (!matcher.matches() || !otherMatcher.matches()) {
                    // Variables don't match constants
                    return false;
                }
                String extension = matcher.group(5);
                String otherExtension = otherMatcher.group(5);
                return Objects.equals(otherExtension, extension);
            }
            if (!patternParts[i].equals(otherPatternParts[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void invoke(UserContext userContext, ApiHttpExchange exchange) throws IOException {
        verifyUserAccess(exchange, userContext);
        calculatePathParams(exchange);
        HttpParameterMapper[] parameterMappers = createParameterMappers(getAction());
        Object[] arguments = createArguments(getAction(), exchange, parameterMappers);
        logger.debug("Invoking {}", this);
        Object result = invoke(getController(), getAction(), arguments);
        for (int i = 0; i < parameterMappers.length; i++) {
            parameterMappers[i].onComplete(exchange, arguments[i]);
        }
        convertReturnValue(result, exchange);
    }

    protected void calculatePathParams(ApiHttpExchange exchange) {
        HashMap<String, String> pathParameters = new HashMap<>();

        String pathInfo = exchange.getPathInfo();
        String[] actualParts = pathInfo.split("/");
        if (patternParts.length != actualParts.length) {
            throw new IllegalArgumentException("Paths don't match <" + pattern + ">, but was <" + pathInfo + ">");
        }

        for (int i = 0; i < patternParts.length; i++) {
            if (pathParams[i] != null) {
                if (paramRegexp[i] != null) {
                    Matcher matcher = paramRegexp[i].matcher(actualParts[i]);
                    if (!matcher.matches()) {
                        throw new IllegalArgumentException("Paths don't match <" + pattern + ">, but was <" + pathInfo + ">");
                    }
                    pathParameters.put(pathParams[i], matcher.group(1));
                } else {
                    pathParameters.put(pathParams[i], actualParts[i]);
                }
            } else if (!patternParts[i].equals(actualParts[i])) {
                throw new IllegalArgumentException("Paths don't match <" + pattern + ">, but was <" + pathInfo + ">");
            }
        }
        exchange.setPathParameters(pathParameters);
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
                logger.info("While invoking {}: {}", getMethodName(action), e.getTargetException().toString());
                throw (HttpActionException)e.getTargetException();
            } else {
                throw ExceptionUtil.softenException(e.getTargetException());
            }
        } catch (IllegalAccessException e) {
            logger.error("While invoking {}", getMethodName(action), e);
            throw new HttpServerErrorException(e);
        }
    }

    private Object[] createArguments(Method method, ApiHttpExchange exchange, HttpParameterMapper[] parameterMappers) throws IOException {
        try {
            Object[] arguments = new Object[parameterMappers.length];
            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = parameterMappers[i].apply(exchange);
                if (!isCorrectType(arguments[i], method.getParameterTypes()[i])) {
                    throw new HttpActionException(500, this + " parameter mapper " + i + " returned wrong type " + arguments[i].getClass() + " (expected " + method.getParameters()[i].getParameterizedType() + ")");
                }
            }
            return arguments;
        } catch (HttpRequestException|HttpRedirectException e) {
            logger.debug("While processing {} arguments to {}: {}", exchange, this, e.toString());
            throw e;
        } catch (HttpActionException e) {
            logger.warn("While processing {} arguments to {}", exchange, this, e);
            StackTraceElement[] stackTrace = e.getStackTrace();
            StackTraceElement[] replacedStackTrace = new StackTraceElement[stackTrace.length + 1];
            replacedStackTrace[0] = new StackTraceElement(method.getDeclaringClass().getName(), method.getName(),
                    method.getDeclaringClass().getSimpleName() + ".java", 1);
            System.arraycopy(stackTrace, 0, replacedStackTrace, 1, stackTrace.length);
            e.setStackTrace(replacedStackTrace);

            throw e;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("While processing {} arguments for {}", exchange, this, e);
            throw new HttpRequestException(e);
        }
    }

    private HttpParameterMapper[] createParameterMappers(Method method) {
        HttpParameterMapper[] parameterMappers = new HttpParameterMapper[method.getParameterCount()];
        for (int i = 0; i < parameterMappers.length; i++) {
            parameterMappers[i] = this.parameterMappers.get(i);
        }
        return parameterMappers;
    }

    private boolean isCorrectType(Object argument, Class<?> requiredType) {
        return argument == null || requiredType.isPrimitive() || requiredType.isAssignableFrom(argument.getClass());
    }

    private void convertReturnValue(Object result, ApiHttpExchange exchange) throws IOException {
        try {
            responseMapper.accept(result, exchange);
        } catch (RuntimeException e) {
            logger.error("While converting {} return value {}", this, result, e);
            throw new HttpActionException(500, "Internal server error while mapping response");
        }
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

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + httpMethod + " " + pattern + " => " + getMethodName() + "}";
    }

    @Override
    public String getMethodName() {
        return getMethodName(action);
    }

    private static String getMethodName(Method action) {
        String parameters = Stream.of(action.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(","));
        return action.getDeclaringClass().getSimpleName() + "." + action.getName() + "(" + parameters + ")";
    }
}

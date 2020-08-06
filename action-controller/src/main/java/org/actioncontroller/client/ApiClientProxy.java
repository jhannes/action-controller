package org.actioncontroller.client;

import org.actioncontroller.DELETE;
import org.actioncontroller.GET;
import org.actioncontroller.POST;
import org.actioncontroller.PUT;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpClientReturnMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpReturnMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Used to dynamically create a client implementation of a REST-ful interface defined through
 * an Action Controller annotated interface.
 *
 * <h3>Usage</h3>
 *
 * Given a Controller interface like the following:
 *
 * <pre>
*  public interface Controller {
 *    &#064;Get("/data")
 *    &#064;ContentBody String getContent(@RequestParam("filter") String filter);
 * }
 * </pre>
 *
 * The following will make a request to http://example.com/data?filter=foo and return the content as a string
 *
 * <pre>
 * String baserUrl = "http://example.com/";
 * Controller client = ApiClientProxy.create(Controller.class, new HttpURLConnectionApiClient(baseUrl));
 * String response = client.getContent("foo");
 * </pre>
 */
public class ApiClientProxy {
    private static final Logger logger = LoggerFactory.getLogger(ApiClientProxy.class);

    public static <T> T create(Class<T> controllerClass, ApiClient client) {
        //noinspection unchecked
        return (T) Proxy.newProxyInstance(controllerClass.getClassLoader(), new Class[] { controllerClass }, createInvocationHandler(client));
    }

    static InvocationHandler createInvocationHandler(ApiClient client) {
        return (proxy, method, args) -> invoke(client, method, args);
    }

    private static Object invoke(ApiClient client, Method method, Object[] args) throws IllegalAccessException, InvocationTargetException, IOException {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(client, args);
        }

        ApiClientExchange exchange = createExchange(client, method);
        HttpReturnMapperFactory.createNewClientInstance(method).ifPresent(mapper -> mapper.setupExchange(exchange));
        processParameters(method, args, exchange);

        long startTime = System.currentTimeMillis();
        logger.trace("{}: {}", getMethodName(method), exchange);
        exchange.executeRequest();
        logger.debug("{}: {} latency={}", getMethodName(method), exchange.getResponseCode(), System.currentTimeMillis()-startTime);

        exchange.checkForError();
        processConsumerParameters(method, args, exchange);
        return transformReturnValue(method, exchange);
    }

    private static Object transformReturnValue(Method method, ApiClientExchange exchange) throws IOException {
        Optional<HttpClientReturnMapper> clientInstance = HttpReturnMapperFactory.createNewClientInstance(method);
        if (clientInstance.isPresent()) {
            Object returnValue = clientInstance.get().getReturnValue(exchange);
            if (!isCorrectType(returnValue, method.getReturnType())) {
                throw new IllegalArgumentException(getMethodName(method) + " returned the wrong type. Expected " + method.getReturnType() + " but was " + returnValue.getClass());
            }

            return returnValue;
        }
        if (method.getReturnType() == Void.TYPE) {
            return null;
        }
        throw new RuntimeException("Unsupported return type for to " + method);
    }

    private static void processConsumerParameters(Method method, Object[] args, ApiClientExchange exchange) throws IOException {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (Consumer.class.isAssignableFrom(parameter.getType())) {
                Optional<HttpClientParameterMapper> clientInstance = HttpParameterMapperFactory.createNewClientInstance(parameter);
                if (clientInstance.isPresent()) {
                    clientInstance.get().apply(exchange, args[i]);
                }
            }
        }
    }

    private static void processParameters(Method method, Object[] args, ApiClientExchange exchange) throws IOException {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (!Consumer.class.isAssignableFrom(parameter.getType())) {
                Optional<HttpClientParameterMapper> clientInstance = HttpParameterMapperFactory.createNewClientInstance(parameter);
                if (clientInstance.isPresent()) {
                    clientInstance.get().apply(exchange, args[i]);
                }
            }
        }
    }

    private static ApiClientExchange createExchange(ApiClient client, Method method) {
        ApiClientExchange exchange = client.createExchange();
        Optional.ofNullable(method.getAnnotation(GET.class))
                .ifPresent(a -> exchange.setTarget("GET", a.value()));
        Optional.ofNullable(method.getAnnotation(POST.class))
                .ifPresent(a -> exchange.setTarget("POST", a.value()));
        Optional.ofNullable(method.getAnnotation(PUT.class))
                .ifPresent(a -> exchange.setTarget("PUT", a.value()));
        Optional.ofNullable(method.getAnnotation(DELETE.class))
                .ifPresent(a -> exchange.setTarget("DELETE", a.value()));
        if (exchange.getRequestMethod() == null) {
            throw new RuntimeException("Unsupported mapping to " + method);
        }
        return exchange;
    }

    private static boolean isCorrectType(Object argument, Class<?> requiredType) {
        return argument == null || requiredType.isPrimitive() || requiredType.isAssignableFrom(argument.getClass());
    }

    private static Object getMethodName(Method action) {
        String parameters = Stream.of(action.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(","));
        return action.getDeclaringClass().getSimpleName() + "." + action.getName() + "(" + parameters + ")";
    }
}

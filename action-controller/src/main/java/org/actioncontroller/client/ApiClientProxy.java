package org.actioncontroller.client;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;
import org.actioncontroller.DELETE;
import org.actioncontroller.GET;
import org.actioncontroller.POST;
import org.actioncontroller.PUT;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpReturnMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Used to dynamically create a client implementation of a REST-ful interface defined through
 * an Action Controller annotated interface or class (requires ByteBuddy).
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
    public static <T> T create(Class<T> controllerClass, ApiClient client) {
        DynamicType.Loaded<?> type = new ByteBuddy()
                .subclass(controllerClass)
                .method(ElementMatchers.any())
                .intercept(InvocationHandlerAdapter.of(createInvocationHandler(client)))
                .make()
                .load(controllerClass.getClassLoader());
        try {
            return (T) type.getLoaded().getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static InvocationHandler createInvocationHandler(ApiClient client) {
        return (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(client, args);
            }


            ApiClientExchange exchange = client.createExchange();
            Optional.ofNullable(method.getAnnotation(GET.class))
                    .ifPresent(a -> exchange.setTarget("GET", a.value()));
            Optional.ofNullable(method.getAnnotation(POST.class))
                    .ifPresent(a -> exchange.setTarget("POST", a.value()));
            Optional.ofNullable(method.getAnnotation(PUT.class))
                    .ifPresent(a -> exchange.setTarget("PUT", a.value()));
            Optional.ofNullable(method.getAnnotation(DELETE.class))
                    .ifPresent(a -> exchange.setTarget("DELETE", a.value()));

            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                if (!Consumer.class.isAssignableFrom(parameter.getType())) {
                    for (Annotation annotation : parameter.getAnnotations()) {
                        HttpParameterMapping parameterMapping = annotation.annotationType().getAnnotation(HttpParameterMapping.class);
                        if (parameterMapping != null) {
                            parameterMapping.value()
                                    .getDeclaredConstructor().newInstance()
                                    .clientParameterMapper(annotation, parameter)
                                    .apply(exchange, args[i]);
                        }
                    }
                }
            }
            if (exchange.getRequestMethod() == null) {
                throw new RuntimeException("Unsupported mapping to " + method);
            }

            exchange.executeRequest();

            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                if (Consumer.class.isAssignableFrom(parameter.getType())) {
                    for (Annotation annotation : parameter.getAnnotations()) {
                        HttpParameterMapping parameterMapping = annotation.annotationType().getAnnotation(HttpParameterMapping.class);
                        if (parameterMapping != null) {
                            parameterMapping.value()
                                    .getDeclaredConstructor().newInstance()
                                    .clientParameterMapper(annotation, parameter)
                                    .apply(exchange, args[i]);
                        }
                    }
                }
            }

            exchange.checkForError();

            for (Annotation annotation : method.getAnnotations()) {
                HttpReturnMapping returnMapping = annotation.annotationType().getAnnotation(HttpReturnMapping.class);
                if (returnMapping != null) {
                    Object returnValue = returnMapping.value()
                            .getDeclaredConstructor()
                            .newInstance()
                            .createClientMapper(annotation, method.getGenericReturnType())
                            .getReturnValue(exchange);
                    if (!isCorrectType(returnValue, method.getReturnType())) {
                        throw new IllegalArgumentException(returnMapping + " returned the wrong type. Expected " + method.getReturnType() + " but was " + returnValue.getClass());
                    }

                    return returnValue;
                }
            }

            if (method.getReturnType() == Void.TYPE) {
                return null;
            }
            throw new RuntimeException("Unsupported return type for to " + method);
        };
    }

    private static boolean isCorrectType(Object argument, Class<?> requiredType) {
        return argument == null || requiredType.isPrimitive() || requiredType.isAssignableFrom(argument.getClass());
    }
}

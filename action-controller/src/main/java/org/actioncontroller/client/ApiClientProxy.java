package org.actioncontroller.client;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;
import org.actioncontroller.Delete;
import org.actioncontroller.Get;
import org.actioncontroller.Post;
import org.actioncontroller.Put;
import org.actioncontroller.UnencryptedCookie;
import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpReturnMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.util.Optional;
import java.util.function.Consumer;

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

            ApiClientExchange exchange = client.createExchange();
            Optional.ofNullable(method.getAnnotation(Get.class))
                    .ifPresent(a -> exchange.setTarget("GET", a.value()));
            Optional.ofNullable(method.getAnnotation(Post.class))
                    .ifPresent(a -> exchange.setTarget("POST", a.value()));
            Optional.ofNullable(method.getAnnotation(Put.class))
                    .ifPresent(a -> exchange.setTarget("PUT", a.value()));
            Optional.ofNullable(method.getAnnotation(Delete.class))
                    .ifPresent(a -> exchange.setTarget("DELETE", a.value()));

            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
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
            if (exchange.getRequestMethod() == null) {
                throw new RuntimeException("Unsupported mapping to " + method);
            }

            exchange.executeRequest();

            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                UnencryptedCookie cookieParam = parameter.getAnnotation(UnencryptedCookie.class);
                if (cookieParam != null && Consumer.class.isAssignableFrom(parameter.getType())) {
                    String cookieValue = exchange.getResponseCookie(cookieParam.value());
                    Object value = ApiHttpExchange.convertParameterType(
                            cookieValue,
                            ((ParameterizedType)parameter.getParameterizedType()).getActualTypeArguments()[0]
                    );
                    ((Consumer) args[i]).accept(value);
                }
            }

            exchange.checkForError();

            for (Annotation annotation : method.getAnnotations()) {
                HttpReturnMapping returnMapping = annotation.annotationType().getAnnotation(HttpReturnMapping.class);
                if (returnMapping != null) {
                    return returnMapping.value()
                            .getDeclaredConstructor()
                            .newInstance()
                            .createClient(annotation, method.getReturnType())
                            .getReturnValue(exchange);
                }
            }

            if (method.getReturnType() == Void.TYPE) {
                return null;
            }
            throw new RuntimeException("Unsupported return type for to " + method);
        };
    }

}

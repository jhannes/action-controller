package org.actioncontroller.test;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;
import org.actioncontroller.ContentBody;
import org.actioncontroller.ContentLocationHeader;
import org.actioncontroller.Delete;
import org.actioncontroller.Get;
import org.actioncontroller.HttpResponseHeader;
import org.actioncontroller.PathParam;
import org.actioncontroller.Post;
import org.actioncontroller.Put;
import org.actioncontroller.RequestParam;
import org.actioncontroller.SendRedirect;
import org.actioncontroller.UnencryptedCookie;
import org.actioncontroller.client.ApiClient;
import org.actioncontroller.client.ApiClientExchange;
import org.actioncontroller.meta.ApiHttpExchange;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.util.Optional;
import java.util.function.Consumer;

public class ApiClientFakeServletProxy {
    public static <T> T create(T controller, ApiClient client) {
        DynamicType.Loaded<?> type = new ByteBuddy()
                .subclass(controller.getClass())
                .method(ElementMatchers.any())
                .intercept(InvocationHandlerAdapter.of(createInvocationHandler(client)))
                .make()
                .load(controller.getClass().getClassLoader());
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

            String pathInfo = exchange.getPathInfo();

            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                if (requestParam != null) {
                    if (args[i] instanceof Optional) {
                        ((Optional)args[i]).ifPresent(p -> {
                            exchange.setRequestParameter(requestParam.value(), p.toString());
                        });
                    } else {
                        exchange.setRequestParameter(requestParam.value(), args[i].toString());
                    }
                }
                PathParam pathParam = parameter.getAnnotation(PathParam.class);
                if (pathParam != null) {
                    pathInfo = pathInfo.replace("/:" + pathParam.value(), "/" + args[i].toString());
                }
                UnencryptedCookie cookieParam = parameter.getAnnotation(UnencryptedCookie.class);
                if (cookieParam != null) {
                    if (args[i] instanceof Optional) {
                        ((Optional)args[i]).ifPresent(p ->
                                exchange.addRequestCookie(cookieParam.value(), p.toString())
                        );
                    } else {
                        exchange.addRequestCookie(cookieParam.value(), args[i].toString());
                    }
                }
            }
            exchange.setPathInfo(pathInfo);
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

            if (exchange.getResponseCode() >= 400) {
                throw new HttpClientException(
                        exchange.getResponseCode(),
                        exchange.getResponseMessage(),
                        exchange.getRequestURL()
                );
            }

            ContentBody contentBodyAnnotation = method.getAnnotation(ContentBody.class);
            if (contentBodyAnnotation != null) {
                return ApiHttpExchange.convertParameterType(exchange.getResponseBody(), method.getReturnType());
            }
            HttpResponseHeader headerAnnotation = method.getAnnotation(HttpResponseHeader.class);
            if (headerAnnotation != null) {
                return ApiHttpExchange.convertParameterType(exchange.getResponseHeader(headerAnnotation.value()), method.getReturnType());
            }
            ContentLocationHeader contentLocationHeader = method.getAnnotation(ContentLocationHeader.class);
            if (contentLocationHeader != null) {
                return exchange.getResponseHeader(ContentLocationHeader.FIELD_NAME);
            }
            SendRedirect sendRedirectHeader = method.getAnnotation(SendRedirect.class);
            if (sendRedirectHeader != null) {
                if (exchange.getResponseCode() < 300) {
                    throw new IllegalArgumentException("Expected redirect, but was " + exchange.getResponseCode());
                }
                return exchange.getResponseHeader("Location");
            }
            if (method.getReturnType() == Void.TYPE) {
                return null;
            }
            throw new RuntimeException("Unsupported return type for to " + method);
        };
    }

}

package org.actioncontroller.test;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;
import org.actioncontroller.ContentBody;
import org.actioncontroller.ContentLocationHeader;
import org.actioncontroller.Get;
import org.actioncontroller.HttpResponseHeader;
import org.actioncontroller.PathParam;
import org.actioncontroller.Post;
import org.actioncontroller.Put;
import org.actioncontroller.RequestParam;
import org.actioncontroller.UnencryptedCookie;
import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.servlet.ApiServlet;

import javax.servlet.ServletException;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.net.URL;
import java.util.Optional;
import java.util.function.Consumer;

public class ApiClientFakeServletProxy {
    public static <T> T create(T controller, URL contextRoot, String servletPath) throws ServletException {
        ApiServlet servlet = new ApiServlet() {
            @Override
            public void init() {
                registerController(controller);
            }
        };
        servlet.init(null);
        DynamicType.Loaded<?> type = new ByteBuddy()
                .subclass(controller.getClass())
                .method(ElementMatchers.any())
                .intercept(InvocationHandlerAdapter.of(createInvocationHandler(servlet, contextRoot, servletPath)))
                .make()
                .load(controller.getClass().getClassLoader());
        try {
            return (T) type.getLoaded().getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static InvocationHandler createInvocationHandler(ApiServlet servlet, URL contextRoot, String servletPath) {
        return (proxy, method, args) -> {
            Get getAnnotation = method.getAnnotation(Get.class);
            if (getAnnotation != null) {
                FakeServletRequest request = createRequest(method, args, "GET", contextRoot, servletPath, getAnnotation.value());
                FakeServletResponse response = new FakeServletResponse();
                servlet.service(request, response);
                return createReturnValue(method, args, request, response);
            }
            Post postAnnotation = method.getAnnotation(Post.class);
            if (postAnnotation != null) {
                FakeServletRequest request = createRequest(method, args, "POST", contextRoot, servletPath, postAnnotation.value());
                FakeServletResponse response = new FakeServletResponse();
                servlet.service(request, response);
                return createReturnValue(method, args, request, response);
            }
            Put putAnnotation = method.getAnnotation(Put.class);
            if (putAnnotation != null) {
                FakeServletRequest request = createRequest(method, args, "PUT", contextRoot, servletPath, putAnnotation.value());
                FakeServletResponse response = new FakeServletResponse();
                servlet.service(request, response);
                return createReturnValue(method, args, request, response);
            }

            throw new RuntimeException("Unsupported mapping to " + method);
        };
    }

    private static FakeServletRequest createRequest(Method action, Object[] args, String method, URL contextRoot, String servletPath, String pathInfo) {
        FakeServletRequest request = new FakeServletRequest(method, contextRoot, servletPath, pathInfo);
        pathInfo = setupRequest(request, action, args, pathInfo);
        request.setPathInfo(pathInfo);
        return request;
    }

    private static String setupRequest(FakeServletRequest request, Method method, Object[] args, String path) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
            if (requestParam != null) {
                if (args[i] instanceof Optional) {
                    ((Optional)args[i]).ifPresent(p -> {
                        request.setParameter(requestParam.value(), p.toString());
                    });
                } else {
                    request.setParameter(requestParam.value(), args[i].toString());
                }
            }
            PathParam pathParam = parameter.getAnnotation(PathParam.class);
            if (pathParam != null) {
                path = path.replace("/:" + pathParam.value(), "/" + args[i].toString());
            }
            UnencryptedCookie cookieParam = parameter.getAnnotation(UnencryptedCookie.class);
            if (cookieParam != null) {
                if (args[i] instanceof Optional) {
                    ((Optional)args[i]).ifPresent(p ->
                        request.setCookie(cookieParam.value(), p.toString())
                    );
                } else {
                    request.setCookie(cookieParam.value(), args[i].toString());
                }
            }
        }
        return path;
    }

    private static Object createReturnValue(Method method, Object[] args, FakeServletRequest request, FakeServletResponse response) throws IOException, HttpClientException {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            UnencryptedCookie cookieParam = parameter.getAnnotation(UnencryptedCookie.class);
            if (cookieParam != null && Consumer.class.isAssignableFrom(parameter.getType())) {
                String cookieValue = response.getCookie(cookieParam.value());
                Object value = ApiHttpExchange.convertParameterType(
                        cookieValue,
                        ((ParameterizedType)parameter.getParameterizedType()).getActualTypeArguments()[0]
                );
                ((Consumer) args[i]).accept(value);
            }
        }


        if (response.getStatus() >= 400) {
            throw new HttpClientException(
                    response.getStatus(),
                    response.getStatusMessage(),
                    new URL(request.getRequestURL().toString())
            );
        }

        ContentBody contentBodyAnnotation = method.getAnnotation(ContentBody.class);
        if (contentBodyAnnotation != null) {
            return ApiHttpExchange.convertParameterType(response.getBody(), method.getReturnType());
        }
        HttpResponseHeader headerAnnotation = method.getAnnotation(HttpResponseHeader.class);
        if (headerAnnotation != null) {
            return ApiHttpExchange.convertParameterType(response.getHeader(headerAnnotation.value()), method.getReturnType());
        }
        ContentLocationHeader contentLocationHeader = method.getAnnotation(ContentLocationHeader.class);
        if (contentLocationHeader != null) {
            return response.getHeader(ContentLocationHeader.FIELD_NAME);
        }
        if (method.getReturnType() == Void.TYPE) {
            return null;
        }
        throw new RuntimeException("Unsupported return type for to " + method);
    }

}

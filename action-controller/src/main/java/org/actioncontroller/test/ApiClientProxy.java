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
import org.actioncontroller.SendRedirect;
import org.actioncontroller.UnencryptedCookie;
import org.actioncontroller.meta.ApiHttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ApiClientProxy {
    public static <T> T create(Class<T> controllerClass, String baseUrl) {
        DynamicType.Loaded<T> type = new ByteBuddy()
                .subclass(controllerClass)
                .method(ElementMatchers.any())
                .intercept(InvocationHandlerAdapter.of(createInvocationHandler(baseUrl)))
                .make()
                .load(controllerClass.getClassLoader());
        try {
            return type.getLoaded().getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static InvocationHandler createInvocationHandler(String baseUrl) {
        List<HttpCookie> clientCookies = new ArrayList<>();
        return (proxy, method, args) -> {
            List<HttpCookie> requestCookies = new ArrayList<>(clientCookies);
            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                UnencryptedCookie cookieParam = parameter.getAnnotation(UnencryptedCookie.class);
                if (cookieParam != null && !Consumer.class.isAssignableFrom(parameter.getType())) {
                    String value = args[i].toString();
                    if (parameter.getType() == Optional.class) {
                        Optional opt = (Optional) args[i];
                        if (!opt.isPresent()) {
                            continue;
                        } else {
                            value = opt.get().toString();
                        }
                    }

                    HttpCookie cookie = new HttpCookie(cookieParam.value(), value);
                    cookie.setPath(new URI(baseUrl).getPath());
                    requestCookies.add(cookie);
                }
            }

            Get getAnnotation = method.getAnnotation(Get.class);
            if (getAnnotation != null) {
                URL url = getUrlWithQuery(baseUrl, getAnnotation.value(), method, args);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("Cookie",
                        requestCookies.stream().map(HttpCookie::toString).collect(Collectors.joining(",")));
                checkResponse(conn);
                clientCookies.addAll(getResponseCookies(conn));
                CookieManager cookieManager = new CookieManager();
                cookieManager.put(url.toURI(), conn.getHeaderFields());
                setConsumedCookies(method, args, cookieManager);
                return createReturnValue(method, conn);
            }
            Post postAnnotation = method.getAnnotation(Post.class);
            if (postAnnotation != null) {
                URL url = getUrl(baseUrl, postAnnotation.value(), method, args);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Cookie",
                        requestCookies.stream().map(HttpCookie::toString).collect(Collectors.joining(",")));
                writeBody(conn, method, args);

                checkResponse(conn);
                clientCookies.addAll(getResponseCookies(conn));
                CookieManager cookieManager = new CookieManager();
                cookieManager.put(url.toURI(), conn.getHeaderFields());
                setConsumedCookies(method, args, cookieManager);
                return createReturnValue(method, conn);
            }
            Put putAnnotation = method.getAnnotation(Put.class);
            if (putAnnotation != null) {
                URL url = getUrl(baseUrl, putAnnotation.value(), method, args);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Cookie",
                        requestCookies.stream().map(HttpCookie::toString).collect(Collectors.joining(",")));
                writeBody(conn, method, args);

                checkResponse(conn);
                clientCookies.addAll(getResponseCookies(conn));
                CookieManager cookieManager = new CookieManager();
                cookieManager.put(url.toURI(), conn.getHeaderFields());
                setConsumedCookies(method, args, cookieManager);
                return createReturnValue(method, conn);
            }


            throw new RuntimeException("Unsupported mapping to " + method);
        };
    }

    private static List<HttpCookie> getResponseCookies(HttpURLConnection conn) {
        List<HttpCookie> responseCookies = new ArrayList<>();
        String setCookieField = conn.getHeaderField("Set-Cookie");
        if (setCookieField != null) {
            responseCookies = HttpCookie.parse(setCookieField);
        }
        return responseCookies;
    }

    private static void writeBody(HttpURLConnection conn, Method method, Object[] args) throws IOException {
        String query = getQuery(method, args);
        if (!query.isEmpty()) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-type", "application/x-www-form-urlencoded");
            conn.getOutputStream().write(query.getBytes());
            conn.getOutputStream().flush();
        }
    }

    private static URL getUrl(String baseUrl, String value, Method method, Object[] args) throws MalformedURLException {
        return new URL(baseUrl + getPath(value, method, args));
    }

    private static Object createReturnValue(Method method, HttpURLConnection conn) throws IOException {
        ContentBody contentBodyAnnotation = method.getAnnotation(ContentBody.class);
        if (contentBodyAnnotation != null) {
            return ApiHttpExchange.convertParameterType(asString(conn.getInputStream()), method.getReturnType());
        }
        HttpResponseHeader headerAnnotation = method.getAnnotation(HttpResponseHeader.class);
        if (headerAnnotation != null) {
            return ApiHttpExchange.convertParameterType(conn.getHeaderField(headerAnnotation.value()), method.getReturnType());
        }
        ContentLocationHeader contentLocationHeader = method.getAnnotation(ContentLocationHeader.class);
        if (contentLocationHeader != null) {
            return conn.getHeaderField(ContentLocationHeader.FIELD_NAME);
        }
        SendRedirect sendRedirect = method.getAnnotation(SendRedirect.class);
        if (sendRedirect != null) {
            if (conn.getResponseCode() < 300) {
                throw new IllegalArgumentException("Expected redirect, but was " + conn.getResponseCode() + " " + conn.getResponseMessage() + " for " + conn.getURL());
            }
            return conn.getHeaderField("Location");
        }
        if (method.getReturnType() == Void.TYPE) {
            return null;
        }
        throw new RuntimeException("Unknown return handling for " + method);
    }

    private static void setConsumedCookies(Method method, Object[] args, CookieManager cookieManager) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            UnencryptedCookie cookieParam = parameter.getAnnotation(UnencryptedCookie.class);
            if (cookieParam != null && Consumer.class.isAssignableFrom(parameter.getType())) {
                String cookieValue = cookieManager.getCookieStore().getCookies().stream()
                        .filter(cookie -> cookie.getName().equals(cookieParam.value()))
                        .map(HttpCookie::getValue)
                        .findFirst().orElse(null);
                Object value = ApiHttpExchange.convertParameterType(
                        cookieValue,
                        ((ParameterizedType)parameter.getParameterizedType()).getActualTypeArguments()[0]
                );
                ((Consumer) args[i]).accept(value);
            }
        }
    }

    private static URL getUrlWithQuery(String baseUrl, String path, Method method, Object[] args) throws MalformedURLException {
        String query = getQuery(method, args);
        return new URL(baseUrl + getPath(path, method, args) + (query.isEmpty() ? "" : "?" + query));
    }

    private static String getQuery(Method method, Object[] args) {
        Map<String, String> requestParameters = new HashMap<>();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
            if (requestParam != null) {
                if (args[i] instanceof Optional) {
                    ((Optional)args[i]).ifPresent(p -> {
                        requestParameters.put(requestParam.value(), p.toString());
                    });
                } else {
                    requestParameters.put(requestParam.value(), args[i].toString());
                }
            }
        }

        return requestParameters
                    .entrySet().stream()
                    .map(entry -> URLEncoder.encode(entry.getKey()) + "=" + URLEncoder.encode(entry.getValue()))
                    .collect(Collectors.joining("&"));
    }

    private static String getPath(String path, Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            PathParam requestParam = parameter.getAnnotation(PathParam.class);
            if (requestParam != null) {
                path = path.replace("/:" + requestParam.value(), "/" + args[i].toString());
            }
        }
        return path;
    }

    private static void checkResponse(HttpURLConnection conn) throws IOException {
        if (conn.getResponseCode() >= 400) {
            throw new HttpClientException(
                    conn.getResponseCode(),
                    conn.getResponseMessage(),
                    conn.getURL()
            );
        }
    }

    private static String asString(InputStream inputStream) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        int c;
        while ((c = inputStream.read()) != -1) {
            stringBuilder.append((char)c);
        }
        return stringBuilder.toString();
    }
}

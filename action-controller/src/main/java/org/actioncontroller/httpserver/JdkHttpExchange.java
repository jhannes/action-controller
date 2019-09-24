package org.actioncontroller.httpserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsExchange;
import org.actioncontroller.HttpActionException;
import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.meta.WriterConsumer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Parameter;
import java.net.HttpCookie;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class JdkHttpExchange implements ApiHttpExchange {
    private final HttpExchange exchange;
    private Map<String, String> pathParams = new HashMap<>();
    private final String context;
    private final String apiPath;
    private Map<String, List<String>> parameters;
    private boolean responseSent = false;

    public JdkHttpExchange(HttpExchange exchange, String context, String apiPath) throws IOException {
        this.exchange = exchange;
        this.context = context;
        this.apiPath = apiPath;
        try {
            this.parameters = parseParameters(exchange.getRequestURI().getQuery());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Should never happen", e);
        }
        if (!exchange.getRequestMethod().equals("GET")) {
            if ("application/x-www-form-urlencoded".equals(exchange.getRequestHeaders().getFirst("content-type"))) {
                this.parameters = parseParameters(asString(exchange.getRequestBody()));
            }
        }
    }

    @Override
    public String getHttpMethod() {
        return exchange.getRequestMethod();
    }

    @Override
    public URL getContextURL() throws MalformedURLException {
        return new URL(getServerURL() + context);
    }

    @Override
    public String getServerURL() {
        String scheme = Optional.ofNullable(exchange.getRequestHeaders().getFirst("X-Forwarded-Proto"))
                .orElse(exchange instanceof HttpsExchange ? "https" : "http");
        String host = Optional.ofNullable(exchange.getRequestHeaders().getFirst("X-Forwarded-Host"))
                .orElseGet(() -> Optional.ofNullable(exchange.getRequestHeaders().getFirst("Host"))
                        .orElseGet(() -> exchange.getLocalAddress().toString()));
        return scheme + "://" + host;
    }

    @Override
    public URL getApiURL() throws MalformedURLException {
        return new URL(getServerURL() + context + apiPath);
    }

    @Override
    public String getPathInfo() {
        String path = exchange.getRequestURI().getPath();
        String controllerPath = context + apiPath;
        return path.substring(controllerPath.length());
    }

    private String asString(InputStream inputStream) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        int c;
        while ((c = inputStream.read()) != -1) {
            stringBuilder.append((char)c);
        }
        return stringBuilder.toString();
    }

    @Override
    public boolean isUserLoggedIn() {
        throw new UnsupportedOperationException(getClass().getName() + " does not implement sessions");
    }

    @Override
    public boolean isUserInRole(String role) {
        throw new UnsupportedOperationException(getClass().getName() + " does not implement sessions");
    }

    @Override
    public void setSessionAttribute(String name, Object value, boolean invalidate) {
        throw new UnsupportedOperationException(getClass().getName() + " does not implement sessions");
    }

    @Override
    public Optional getSessionAttribute(String name, boolean createIfMissing) {
        throw new UnsupportedOperationException(getClass().getName() + " does not implement sessions");
    }

    @Override
    public void write(String contentType, WriterConsumer consumer) throws IOException {
        exchange.getResponseHeaders().set("Content-type", contentType);
        sendResponseHeaders(200, 0);
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(exchange.getResponseBody()));
        consumer.accept(writer);
        writer.flush();
    }

    @Override
    public String getHeader(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    @Override
    public void setResponseHeader(String key, String value) {
        exchange.getResponseHeaders().set(key, value);
    }

    @Override
    public void sendRedirect(String path) throws IOException {
        exchange.getResponseHeaders().set("Location", path);
        sendResponseHeaders(302, 0);
    }

    @Override
    public Object pathParam(String name, Parameter parameter) throws HttpActionException {
        String result = this.pathParams.get(name);
        if (result == null) {
            throw new HttpActionException(500, "Path parameter :" + name + " not matched");
        }
        return ApiHttpExchange.convertTo(result, name, parameter);
    }

    @Override
    public void setPathParameters(Map<String, String> pathParameters) {
        this.pathParams = pathParameters;
    }

    @Override
    public Reader getReader() {
        return new InputStreamReader(exchange.getRequestBody());
    }

    @Override
    public String getClientIp() {
        return Optional.ofNullable(exchange.getRequestHeaders().getFirst("X-Forwarded-For"))
                .orElse(exchange.getRemoteAddress().getAddress().getHostAddress());
    }

    @Override
    public String getQueryString() {
        return exchange.getRequestURI().getQuery();
    }

    @Override
    public Object getParameter(String name, Parameter parameter) {
        return ApiHttpExchange.convertTo(getFirstParameter(name), name, parameter);
    }

    @Override
    public boolean hasParameter(String name) {
        return this.parameters.containsKey(name);
    }

    public String getFirstParameter(String name) {
        List<String> parameters = this.parameters.get(name);
        return parameters != null && !parameters.isEmpty() ? parameters.get(0) : null;
    }

    @Override
    public void setCookie(String name, String value, boolean secure) {
        HttpCookie httpCookie = new HttpCookie(name, value);
        httpCookie.setSecure(secure);
        if (value == null) {
            // HACK: HttpCookie doesn't serialize to Set-Cookie format! More work is needed
            exchange.getResponseHeaders().add("Set-Cookie", httpCookie.toString() + "; Max-age=0");
        } else {
            exchange.getResponseHeaders().add("Set-Cookie", httpCookie.toString());
        }
    }

    @Override
    public String getCookie(String name) {
        if (!exchange.getRequestHeaders().containsKey("Cookie")) {
            return null;
        }
        return HttpCookie.parse(exchange.getRequestHeaders().getFirst("Cookie"))
                .stream()
                .filter(c -> c.getName().equals(name))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElse(null);
    }

    @Override
    public void sendError(int statusCode, String message) throws IOException {
        sendResponseHeaders(statusCode, message.getBytes().length);
        exchange.getResponseBody().write(message.getBytes());
    }

    @Override
    public void sendError(int statusCode) throws IOException {
        sendResponseHeaders(statusCode, 0);
    }

    protected Map<String, List<String>> parseParameters(String query) throws UnsupportedEncodingException {
        if (query == null) {
            return new HashMap<>();
        }
        Map<String, List<String>> result = new HashMap<>();
        for (String parameterString : query.split("&")) {
            int equalsPos = parameterString.indexOf('=');
            if (equalsPos > 0) {
                String paramName = parameterString.substring(0, equalsPos);
                String paramValue = URLDecoder.decode(parameterString.substring(equalsPos+1), "ISO-8859-1");
                result.computeIfAbsent(paramName, n -> new ArrayList<>()).add(paramValue);
            }
        }
        return result;
    }

    public void close() throws IOException {
        if (!responseSent) {
            sendResponseHeaders(200, 0);
        }
        exchange.close();
    }

    private void sendResponseHeaders(int rCode, int responseLength) throws IOException {
        exchange.sendResponseHeaders(rCode, responseLength);
        responseSent = true;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + getHttpMethod() + " " +  context + apiPath  + "[" + getPathInfo() + "]}";
    }

    public void calculatePathParams(String[] patternParts) {
        HashMap<String, String> pathParameters = new HashMap<>();

        String[] actualParts = getPathInfo().split("/");
        if (patternParts.length != actualParts.length) {
            throw new IllegalArgumentException("Paths don't match <" + String.join("/", patternParts) + ">, but was <" + getPathInfo() + ">");
        }

        for (int i = 0; i < patternParts.length; i++) {
            if (patternParts[i].startsWith(":")) {
                pathParameters.put(patternParts[i].substring(1), actualParts[i]);
            } else if (!patternParts[i].equals(actualParts[i])) {
                throw new IllegalArgumentException("Paths don't match <" + String.join("/", patternParts) + ">, but was <" + getPathInfo() + ">");
            }
        }
        setPathParameters(pathParameters);
    }
}

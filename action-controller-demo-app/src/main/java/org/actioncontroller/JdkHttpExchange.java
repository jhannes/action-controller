package org.actioncontroller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsExchange;
import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.meta.WriterConsumer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Parameter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class JdkHttpExchange implements ApiHttpExchange {
    private final HttpExchange exchange;
    private Map<String, String> pathParams = new HashMap<>();
    private final String context;
    private final String apiPath;
    private final Map<String, String[]> parameters;

    public JdkHttpExchange(HttpExchange exchange, String context, String apiPath) {
        this.exchange = exchange;
        this.context = context;
        this.apiPath = apiPath;
        try {
            this.parameters = parseParameters(exchange.getRequestURI().getQuery());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Should never happen", e);
        }
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
    public Optional getSessionAttribute(String name) {
        throw new UnsupportedOperationException(getClass().getName() + " does not implement sessions");
    }

    @Override
    public void write(String contentType, WriterConsumer consumer) throws IOException {
        exchange.getResponseHeaders().set("Content-type", contentType);
        exchange.sendResponseHeaders(200, 0);
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(exchange.getResponseBody()));
        consumer.accept(writer);
        writer.flush();
    }

    @Override
    public void setResponseHeader(String key, String value) {
        exchange.getResponseHeaders().set(key, value);
    }

    @Override
    public void sendRedirect(String path) throws IOException {
        exchange.getResponseHeaders().set("Location", path);
        exchange.sendResponseHeaders(302, 0);
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
    public Object getParameter(String name, Parameter parameter) {
        return ApiHttpExchange.convertTo(getFirstParameter(name), name, parameter);
    }

    public String getFirstParameter(String name) {
        String[] parameters = this.parameters.get(name);
        return parameters != null && parameters.length > 0 ? parameters[0] : null;
    }

    @Override
    public void setCookie(String name, String value, boolean secure) {
        if (value == null) {
            String cookie = name + "=; Max-Age=0";
            if (secure) {
                cookie += "; secure=true";
            }
            exchange.getResponseHeaders().set("Set-Cookie", cookie);
        } else {
            String cookie = name + "=" + value;
            if (secure) {
                cookie += "; secure=true";
            }
            exchange.getResponseHeaders().set("Set-Cookie", cookie);
        }
    }

    @Override
    public Object getCookie(String name, Parameter parameter) {
        if (!exchange.getRequestHeaders().containsKey("Cookie")) {
            return Optional.empty();
        }
        String[] cookies = exchange.getRequestHeaders().getFirst("Cookie").split(";\\s+");
        for (String cookie : cookies) {
            int equalPos = cookie.indexOf('=');
            if (equalPos < 0) continue;
            if (cookie.substring(0, equalPos).equals(name)) {
                return Optional.of(cookie.substring(equalPos + 1));
            }
        }
        return Optional.empty();
    }

    @Override
    public void sendError(int statusCode, String message) throws IOException {
        exchange.sendResponseHeaders(statusCode, message.getBytes().length);
        exchange.getResponseBody().write(message.getBytes());
    }

    @Override
    public void sendError(int statusCode) throws IOException {
        exchange.sendResponseHeaders(statusCode, 0);
    }

    protected Map<String, String[]> parseParameters(String query) throws UnsupportedEncodingException {
        if (query == null) {
            return new HashMap<>();
        }
        Map<String, String[]> result = new HashMap<>();
        for (String parameterString : query.split("&")) {
            int equalsPos = parameterString.indexOf('=');
            if (equalsPos > 0) {
                String paramName = parameterString.substring(0, equalsPos);
                String paramValue = URLDecoder.decode(parameterString.substring(equalsPos+1), "ISO-8859-1");
                String[] existingValue = result.get(paramName);
                if (existingValue != null) {
                    String[] newValue = new String[existingValue.length+1];
                    System.arraycopy(existingValue, 0, newValue, 0, existingValue.length);
                    newValue[newValue.length-1] = paramValue;
                    result.put(paramName, newValue);
                } else {
                    result.put(paramName, new String[] { paramValue });
                }
            }
        }
        return result;
    }
}

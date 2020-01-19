package org.actioncontroller.httpserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsExchange;
import org.actioncontroller.HttpActionException;
import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.meta.OutputStreamConsumer;
import org.actioncontroller.meta.WriterConsumer;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.reflect.Parameter;
import java.net.HttpCookie;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.actioncontroller.ExceptionUtil.softenException;

class JdkHttpExchange implements ApiHttpExchange {
    private final HttpExchange exchange;
    private Map<String, String> pathParams = new HashMap<>();
    private final String contextPath;
    private Map<String, List<String>> parameters;
    private boolean responseSent = false;

    public JdkHttpExchange(HttpExchange exchange) throws IOException {
        this.exchange = exchange;
        this.contextPath = exchange.getHttpContext().getPath().equals("/") ? "" : exchange.getHttpContext().getPath();
        this.parameters = parseParameters(exchange.getRequestURI().getQuery());
        if (!exchange.getRequestMethod().equals("GET")) {
            if ("application/x-www-form-urlencoded".equals(exchange.getRequestHeaders().getFirst("content-type"))) {
                this.parameters = parseParameters(asString(exchange.getRequestBody()));
            }
        }
    }

    /**
     * Break the encapsulation of ApiHttpExchange and access the underlying implementation directory.
     * Should be avoided - prefer to improve ApiHttpExchange
     */
    public HttpExchange getExchange() {
        return exchange;
    }

    @Override
    public String getHttpMethod() {
        return exchange.getRequestMethod();
    }

    @Override
    public URL getContextURL() {
        return toURL(getServerURL() + contextPath);
    }

    private URL toURL(String s) {
        try {
            return new URL(s);
        } catch (MalformedURLException e) {
            throw softenException(e);
        }
    }

    @Override
    public String getServerURL() {
        return getScheme() + "://" + getHost();
    }

    private String getScheme() {
        return Optional.ofNullable(exchange.getRequestHeaders().getFirst("X-Forwarded-Proto"))
                .orElse(exchange instanceof HttpsExchange ? "https" : "http");
    }

    private String getHost() {
        return Optional.ofNullable(exchange.getRequestHeaders().getFirst("X-Forwarded-Host")).orElseGet(this::calculateHost);
    }

    private String calculateHost() {
        return getServerName() + (getServerPort() == getDefaultPort() ? "" : ":" + getServerPort());
    }

    private String getServerName() {
        String hostHeader = exchange.getRequestHeaders().getFirst("Host");
        if (hostHeader == null) {
            return exchange.getLocalAddress().getHostName();
        }
        int colonPos = hostHeader.indexOf(':');
        return colonPos == -1 ? hostHeader : hostHeader.substring(0, colonPos);
    }

    private int getServerPort() {
        String hostHeader = exchange.getRequestHeaders().getFirst("Host");
        if (hostHeader == null) {
            return exchange.getLocalAddress().getPort();
        }
        int colonPos = hostHeader.indexOf(':');
        return colonPos == -1 ? getDefaultPort() : Integer.parseInt(hostHeader.substring(colonPos+1));
    }

    private int getDefaultPort() {
        return getScheme().equals("https") ? 443 : (getScheme().equals("http") ? 80 : -1);
    }

    @Override
    public URL getApiURL() {
        return toURL(getServerURL() + contextPath);
    }

    @Override
    public String getPathInfo() {
        String path = exchange.getRequestURI().getPath();
        return path.substring(contextPath.length());
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
    public void output(String contentType, OutputStreamConsumer consumer) throws IOException {
        exchange.getResponseHeaders().set("Content-type", contentType);
        sendResponseHeaders(200, 0);
        OutputStream outputStream = exchange.getResponseBody();
        consumer.accept(outputStream);
        outputStream.flush();
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
    public InputStream getInputStream() {
        return exchange.getRequestBody();
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
        return ApiHttpExchange.convertTo(getParameter(name), name, parameter);
    }

    @Override
    public String getParameter(String name) {
        List<String> parameterValues = this.parameters.get(name);
        return parameterValues != null && !parameterValues.isEmpty() ? parameterValues.get(0) : null;
    }

    @Override
    public boolean hasParameter(String name) {
        return this.parameters.containsKey(name);
    }

    @Override
    public void setCookie(String name, String value, boolean secure) {
        if (getServerName().equals("localhost") && !getScheme().equals("https")) {
            secure = false;
        }

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

    protected Map<String, List<String>> parseParameters(String query) {
        if (query == null) {
            return new HashMap<>();
        }
        Map<String, List<String>> result = new HashMap<>();
        for (String parameterString : query.split("&")) {
            int equalsPos = parameterString.indexOf('=');
            if (equalsPos > 0) {
                String paramName = parameterString.substring(0, equalsPos);
                String paramValue = URLDecoder.decode(parameterString.substring(equalsPos+1), StandardCharsets.ISO_8859_1);
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
    public X509Certificate[] getClientCertificate() {
        try {
            return (X509Certificate[]) ((HttpsExchange)exchange).getSSLSession().getPeerCertificates();
        } catch (SSLPeerUnverifiedException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + getHttpMethod() + " " + contextPath + "[" + getPathInfo() + "]}";
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

package org.actioncontroller.httpserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.sun.net.httpserver.HttpsExchange;
import org.actioncontroller.ActionControllerCookie;
import org.actioncontroller.ApiHttpExchange;
import org.actioncontroller.exceptions.HttpActionException;
import org.actioncontroller.exceptions.HttpServerErrorException;
import org.actioncontroller.util.HttpUrl;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.actioncontroller.util.ExceptionUtil.softenException;

public class JdkHttpExchange implements ApiHttpExchange, AutoCloseable {
    public static final Charset CHARSET = StandardCharsets.ISO_8859_1;

    private final HttpExchange exchange;
    private final Supplier<Map<String, List<String>>> cookies;
    private Map<String, String> pathParams = new HashMap<>();
    private final String contextPath;
    private final Map<String, List<String>> parameters;
    private boolean responseSent = false;

    public JdkHttpExchange(HttpExchange exchange) {
        this.exchange = exchange;
        this.contextPath = exchange.getHttpContext().getPath().equals("/") ? "" : exchange.getHttpContext().getPath();
        if (exchange.getRequestMethod().equals("GET")) {
            this.parameters = HttpUrl.parseParameters(exchange.getRequestURI().getQuery());
        } else if ("application/x-www-form-urlencoded".equals(exchange.getRequestHeaders().getFirst("content-type"))) {
            this.parameters = HttpUrl.parseParameters(asString(exchange.getRequestBody()));
        } else {
            this.parameters = Map.of();
        }
        cookies = ActionControllerCookie.parseClientCookieMap(exchange.getRequestHeaders().get("Cookie"));
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
    public String getContextPath() {
        return contextPath;
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
    public String getPathInfo() {
        String path = exchange.getRequestURI().getPath();
        return path.substring(getContextPath().length());
    }

    private String asString(InputStream inputStream) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            inputStream.transferTo(buffer);
            return buffer.toString();
        } catch (IOException e) {
            throw softenException(e);
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
    public Optional<?> getSessionAttribute(String name, boolean createIfMissing) {
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
    public void writeBody(String contentType, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-type", contentType);
        sendResponseHeaders(200, 0);
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(exchange.getResponseBody()));
        writer.write(body);
        writer.flush();
    }

    @Override
    public void output(String contentType, OutputStreamConsumer consumer) throws IOException {
        if (!exchange.getResponseHeaders().containsKey("Content-type")) {
            exchange.getResponseHeaders().set("Content-type", contentType);
        }
        sendResponseHeaders(200, 0);
        OutputStream outputStream = exchange.getResponseBody();
        consumer.accept(outputStream);
        outputStream.flush();
    }

    @Override
    public List<String> getHeaders(String name) {
        return exchange.getRequestHeaders().get(name);
    }

    @Override
    public Map<String, List<String>> getAllHeaders() {
        return Collections.unmodifiableMap(exchange.getRequestHeaders());
    }

    @Override
    public void setResponseHeader(String key, String value) {
        exchange.getResponseHeaders().set(key, value);
    }

    @Override
    public void addResponseHeader(String key, String value) {
        exchange.getResponseHeaders().add(key, value);
    }

    @Override
    public void sendRedirect(String path) throws IOException {
        exchange.getResponseHeaders().set("Location", path);
        sendResponseHeaders(302, 0);
    }

    @Override
    public String pathParam(String name) throws HttpActionException {
        String result = this.pathParams.get(name);
        if (result == null) {
            throw new HttpServerErrorException("Path parameter :" + name + " not matched");
        }
        return result;
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
    public String getRequestURL() {
        return exchange.getRequestURI().toString();
    }

    @Override
    public String getQueryString() {
        return exchange.getRequestURI().getQuery();
    }

    @Override
    public List<String> getParameters(String name) {
        return this.parameters.get(name);
    }

    @Override
    public void setCookie(
            String name,
            String value,
            boolean secure,
            boolean isHttpOnly,
            String contextPath,
            int maxAge,
            String domain,
            String comment
    ) {
        if (getServerName().equals("localhost") && !getScheme().equals("https")) {
            secure = false;
        }
        exchange.getResponseHeaders().add(
                "Set-Cookie",
                new ActionControllerCookie(name, value)
                    .secure(secure)
                    .httpOnly(isHttpOnly)
                    .path(contextPath)
                    .maxAge(maxAge)
                    .domain(domain)
                    .setAttribute("Comment", comment)
                    .toStringRFC6265()
        );
    }

    @Override
    public List<String> getCookies(String name) {
        return cookies.get().getOrDefault(name, List.of());
    }

    private String getErrorResponse(int statusCode, String message) {
        if (accept("application/json")) {
            return "{\"message\":\"" + message + "\"}";
        } else if (accept("text/html")) {
            return "<body><h2>Error " + statusCode + " " + message +  "</h2><table><tr><th>MESSAGE:</th><td>" + message + "</td></tr></table></body>";
        } else {
            return "MESSAGE: " + message;
        }
    }

    @Override
    public void setStatus(int statusCode) throws IOException {
        sendResponseHeaders(statusCode, -1);
    }

    @Override
    public void sendError(int statusCode) throws IOException {
        sendResponseHeaders(statusCode, -1);
    }

    @Override
    public void sendError(int statusCode, String message) throws IOException {
        String body = getErrorResponse(statusCode, message);
        sendResponseHeaders(statusCode, body.getBytes().length);
        exchange.getResponseBody().write(body.getBytes());
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
    public Principal getUserPrincipal() {
        HttpPrincipal principal = exchange.getPrincipal();
        if (principal instanceof NestedHttpPrincipal) {
            return ((NestedHttpPrincipal)principal).getPrincipal();
        }
        return principal;
    }

    @Override
    public void authenticate() throws IOException {
        if (exchange.getHttpContext().getAuthenticator() instanceof ActionAuthenticator) {
            ((ActionAuthenticator)exchange.getHttpContext().getAuthenticator()).login(this);
        } else {
            sendError(401, "Unauthenticated");
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + getHttpMethod() + " " + getContextPath() + "[" + getPathInfo() + "]}";
    }
}

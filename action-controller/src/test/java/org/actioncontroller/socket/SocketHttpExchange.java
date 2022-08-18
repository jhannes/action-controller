package org.actioncontroller.socket;

import org.actioncontroller.ActionControllerCookie;
import org.actioncontroller.ApiHttpExchange;
import org.actioncontroller.exceptions.HttpActionException;
import org.actioncontroller.exceptions.HttpServerErrorException;
import org.actioncontroller.util.HttpUrl;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.Socket;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class SocketHttpExchange implements ApiHttpExchange, AutoCloseable {
    public static final Map<Integer, String> REASON_PHRASES = Map.ofEntries(
            Map.entry(200, "OK"),
            Map.entry(201, "Created"),
            Map.entry(202, "Accepted"),
            Map.entry(301, "Moved Permanently"),
            Map.entry(302, "Found"),
            Map.entry(303, "See Other"),
            Map.entry(307, "Temporary Redirect"),
            Map.entry(400, "Bad request"),
            Map.entry(401, "Unauthorized"),
            Map.entry(403, "Forbidden"),
            Map.entry(404, "Not Found"),
            Map.entry(500, "Internal Server Error"),
            Map.entry(503, "Service Unavailable")
    );

    private final String requestMethod;
    private final String requestTarget;
    private final Map<String, List<String>> requestHeaders;
    private final LinkedHashMap<String, List<String>> responseHeaders = new LinkedHashMap<>();
    private final Map<String, List<String>> parameters;
    private final Socket socket;
    private final String contextPath = "";
    private final Supplier<Map<String, List<String>>> cookies;
    private boolean headersSent = false;
    private Map<String, String> pathParameters;

    public SocketHttpExchange(Socket socket) throws IOException {
        this.socket = socket;
        String responseLine = SocketHttpClient.readLine(socket.getInputStream());

        String[] parts = responseLine.split(" ", 3);
        this.requestMethod = parts[0];
        this.requestTarget = parts[1];
        this.requestHeaders = SocketHttpClient.readHttpHeaders(socket.getInputStream());
        this.cookies = ActionControllerCookie.parseClientCookieMap(requestHeaders.get("Cookie"));

        if (requestMethod.equals("GET")) {
            this.parameters = HttpUrl.parseParameters(getQueryString());
        } else if (firstHeader("Content-Type").map(s -> s.equalsIgnoreCase("application/x-www-form-urlencoded")).orElse(false)) {
            this.parameters = HttpUrl.parseParameters(new String(getRequestBody()));
        } else {
            this.parameters = new LinkedHashMap<>();
        }
    }

    private byte[] getRequestBody() throws IOException {
        int contentLength = Integer.parseInt(firstHeader("Content-Length").orElseThrow());
        byte[] body = new byte[contentLength];
        getInputStream().read(body);
        return body;
    }

    @Override
    public String getHttpMethod() {
        return requestMethod;
    }

    @Override
    public String getContextPath() {
        return contextPath;
    }

    @Override
    public String getServerURL() {
        return getScheme() + "://" + getHost();
    }

    @Override
    public String getPathInfo() {
        int questionPos = requestTarget.indexOf('?');
        return questionPos >= 0 ? requestTarget.substring(0, questionPos) : requestTarget;
    }

    @Override
    public String getQueryString() {
        int questionPos = requestTarget.indexOf('?');
        return questionPos >= 0 ? requestTarget.substring(questionPos+1) : null;
    }

    @Override
    public List<String> getParameters(String name) {
        return parameters.get(name);
    }

    private String getHost() {
        return firstHeader("X-Forwarded-Host").orElseGet(this::calculateHost);
    }

    private String calculateHost() {
        return getServerName() + (getServerPort() == getDefaultPort() ? "" : ":" + getServerPort());
    }

    private int getServerPort() {
        String hostHeader = firstHeader("Host").orElse(null);
        if (hostHeader == null) {
            return socket.getLocalPort();
        }
        int colonPos = hostHeader.indexOf(':');
        return colonPos == -1 ? getDefaultPort() : Integer.parseInt(hostHeader.substring(colonPos + 1));
    }

    private int getDefaultPort() {
        return getScheme().equals("https") ? 443 : (getScheme().equals("http") ? 80 : -1);
    }

    private String getScheme() {
        return firstHeader("X-Forwarded-Proto")
                .orElse(socket instanceof SSLSocket ? "https" : "http");
    }


    private String getServerName() {
        Optional<String> hostHeader = firstHeader("Host");
        if (!hostHeader.isPresent()) {
            return socket.getLocalAddress().getHostName();
        }
        int colonPos = hostHeader.get().indexOf(':');
        return colonPos == -1 ? hostHeader.get() : hostHeader.get().substring(0, colonPos);
    }

    private Optional<String> firstHeader(String name) {
        List<String> headers = getHeaders(name);
        return headers.isEmpty() ? Optional.empty() : Optional.of(headers.get(0));
    }

    @Override
    public void write(String contentType, WriterConsumer consumer) throws IOException {
        output(contentType, output -> {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(output));
            consumer.accept(writer);
            writer.flush();
        });
    }

    @Override
    public void writeBody(String contentType, String body) throws IOException {
        write(contentType, writer -> writer.write(body));
    }

    @Override
    public void output(String contentType, OutputStreamConsumer consumer) throws IOException {
        if (!responseHeaders.containsKey("Content-Type")) {
            setResponseHeader("Content-Type", contentType);
        }
        sendResponseHeaders(200);
        consumer.accept(socket.getOutputStream());
    }

    @Override
    public List<String> getHeaders(String name) {
        return requestHeaders.containsKey(name) ? requestHeaders.get(name) : List.of();
    }

    @Override
    public String getClientIp() {
        return socket.getInetAddress().getHostAddress();
    }

    @Override
    public void addResponseHeader(String key, String value) {
        responseHeaders
                .computeIfAbsent(key, k -> new ArrayList<>())
                .add(value);
    }

    @Override
    public void setResponseHeader(String key, String value) {
        responseHeaders.put(key, new ArrayList<>(List.of(value)));
    }

    @Override
    public void sendRedirect(String path) throws IOException {
        setResponseHeader("Location", path);
        sendResponseHeaders(302);
    }

    private void sendResponseHeaders(int statusCode) throws IOException {
        sendResponseHeaders(statusCode, REASON_PHRASES.getOrDefault(statusCode, "Unknown"));
    }

    private void sendResponseHeaders(int statusCode, String reasonPhrase) throws IOException {
        if (headersSent) {
            throw new IllegalStateException();
        }
        socket.getOutputStream().write(("HTTP/1.1 " + statusCode + " " + reasonPhrase + "\r\n").getBytes());
        for (Map.Entry<String, List<String>> headers : responseHeaders.entrySet()) {
            for (String value : headers.getValue()) {
                socket.getOutputStream().write((headers.getKey() + ": " + value + "\r\n").getBytes());
            }
        }
        socket.getOutputStream().write("\r\n".getBytes());
        this.headersSent = true;
    }

    @Override
    public String pathParam(String name) throws HttpActionException {
        String result = this.pathParameters.get(name);
        if (result == null) {
            throw new HttpServerErrorException("Path parameter :" + name + " not matched");
        }
        return result;
    }

    @Override
    public void setPathParameters(Map<String, String> pathParameters) {
        this.pathParameters = pathParameters;
    }

    @Override
    public Reader getReader() throws IOException {
        return new InputStreamReader(getInputStream());
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new HttpInputStream(socket.getInputStream(), requestHeaders);
    }

    @Override
    public void setCookie(String name, String value, boolean secure, boolean isHttpOnly, String contextPath, int maxAge, String domain, String comment) {
        addResponseHeader(
                "Set-Cookie",
                new ActionControllerCookie(name, value)
                        .secure(secure).httpOnly(isHttpOnly).path(contextPath).maxAge(maxAge).domain(domain)
                        .toStringRFC6265()
        );
    }

    private String emptyIfNull(String s) {
        return s != null ? s : "";
    }

    @Override
    public List<String> getCookies(String name) {
        return cookies.get().get(name);
    }

    @Override
    public void sendError(int statusCode, String message) throws IOException {
        byte[] content = getErrorResponse(statusCode, message).getBytes();
        setResponseHeader("Content-Type", "text/plain");
        setResponseHeader("Content-Length", String.valueOf(content.length));
        sendResponseHeaders(statusCode);
        socket.getOutputStream().write(content);
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
    public void sendError(int statusCode) throws IOException {
        sendResponseHeaders(statusCode);
    }

    @Override
    public void setStatus(int statusCode) throws IOException {
        sendResponseHeaders(statusCode);
    }

    @Override
    public boolean isUserLoggedIn() {
        return false;
    }

    @Override
    public boolean isUserInRole(String role) {
        return false;
    }

    @Override
    public void setSessionAttribute(String name, Object value, boolean invalidate) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Optional getSessionAttribute(String name, boolean createIfMissing) {
        return Optional.empty();
    }

    @Override
    public X509Certificate[] getClientCertificate() {
        return new X509Certificate[0];
    }

    @Override
    public Principal getUserPrincipal() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void authenticate() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void close() throws IOException {
        if (!headersSent) {
            sendResponseHeaders(200);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + getHttpMethod() + " " + getContextPath() + "[" + getPathInfo() + "]}";
    }
}

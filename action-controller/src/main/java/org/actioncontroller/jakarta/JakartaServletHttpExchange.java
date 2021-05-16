package org.actioncontroller.jakarta;

import org.actioncontroller.util.ExceptionUtil;
import org.actioncontroller.exceptions.HttpActionException;
import org.actioncontroller.exceptions.HttpServerErrorException;
import org.actioncontroller.util.IOUtil;
import org.actioncontroller.ApiHttpExchange;
import org.actioncontroller.meta.OutputStreamConsumer;
import org.actioncontroller.meta.WriterConsumer;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class JakartaServletHttpExchange implements ApiHttpExchange {

    public static final Charset CHARSET = StandardCharsets.ISO_8859_1;
    private final HttpServletRequest req;
    private final HttpServletResponse resp;
    private Map<String, String> pathParams = new HashMap<>();

    public JakartaServletHttpExchange(HttpServletRequest req, HttpServletResponse resp) {
        this.req = req;
        this.resp = resp;
    }

    /**
     * Break the encapsulation of ApiHttpExchange and access the underlying implementation directory.
     * Should be avoided - prefer to improve ApiHttpExchange
     */
    public HttpServletRequest getRequest() {
        return req;
    }

    /**
     * Break the encapsulation of ApiHttpExchange and access the underlying implementation directory.
     * Should be avoided - prefer to improve ApiHttpExchange
     */
    public HttpServletResponse getResponse() {
        return resp;
    }

    @Override
    public String getHttpMethod() {
        return req.getMethod();
    }

    @Override
    public String getServerURL() {
        String host = Optional.ofNullable(req.getHeader("X-Forwarded-Host"))
                .orElseGet(() -> req.getServerName() + (getServerPort() != getDefaultPort() ?  ":" + getServerPort() : ""));
        return getScheme() + "://" + host;
    }

    private int getServerPort() {
        return Optional.ofNullable(req.getHeader("X-Forwarded-Port"))
                .map(Integer::parseInt)
                .orElseGet(() -> {
                    int port = req.getServerPort();
                    return port == 80 || port == 443 ? getDefaultPort() : port;
                });
    }

    private String getScheme() {
        return Optional.ofNullable(req.getHeader("X-Forwarded-Proto")).orElse(req.getScheme());
    }

    private int getDefaultPort() {
        return getScheme().equals("https") ? 443 : (getScheme().equals("http") ? 80 : -1);
    }

    @Override
    public URL getContextURL() {
        return IOUtil.asURL(getServerURL() + getContextPath());
    }

    @Override
    public String getContextPath() {
        return req.getContextPath();
    }

    @Override
    public URL getApiURL() {
        return IOUtil.asURL(getServerURL() + getContextPath() + req.getServletPath());
    }

    @Override
    public String getPathInfo() {
        return req.getPathInfo() != null ? req.getPathInfo() : "";
    }

    @Override
    public void write(String contentType, WriterConsumer consumer) throws IOException {
        resp.setContentType(contentType);
        // BUG: Jetty "calculates" UTF-8 for application/json for resp.getWriter, but doesn't explicitly set character encoding in the header
        resp.setCharacterEncoding(resp.getCharacterEncoding());
        PrintWriter writer = resp.getWriter();
        consumer.accept(writer);
        writer.flush();
    }

    @Override
    public void output(String contentType, OutputStreamConsumer consumer) throws IOException {
        if (resp.getContentType() == null) {
            resp.setContentType(contentType);
        }
        ServletOutputStream outputStream = resp.getOutputStream();
        consumer.accept(outputStream);
        outputStream.flush();
    }

    @Override
    public void setResponseHeader(String key, String value) {
        resp.setHeader(key, value);
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        resp.sendRedirect(location);
    }

    @Override
    public String pathParam(String name) throws HttpActionException {
        String result = this.pathParams.get(name);
        if (result == null) {
            throw new HttpServerErrorException("Path parameter :" + name + " not matched in " + pathParams.keySet());
        }
        return result;
    }

    @Override
    public void setPathParameters(Map<String, String> pathParameters) {
        this.pathParams = pathParameters;
    }

    @Override
    public List<String> getParameters(String name) {
        String[] values = req.getParameterValues(name);
        return values != null ? Arrays.asList(values) : null;
    }

    @Override
    public String getQueryString() {
        return req.getQueryString();
    }

    @Override
    public Reader getReader() throws IOException {
        return req.getReader();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return req.getInputStream();
    }

    @Override
    public String getClientIp() {
        return Optional.ofNullable(req.getHeader("X-Forwarded-For")).orElse(req.getRemoteAddr());
    }

    @Override
    public void setCookie(
            String name,
            String value,
            boolean secure,
            boolean isHttpOnly,
            String path,
            int maxAge,
            String domain,
            String comment
    ) {
        if (req.getServerName().equals("localhost") && !req.isSecure()) {
            secure = false;
        }
        Cookie cookie;
        if (value == null) {
            cookie = new Cookie(name, "");
            cookie.setMaxAge(0);
        } else {
            cookie = new Cookie(name, URLEncoder.encode(value, CHARSET));
            cookie.setMaxAge(maxAge);
        }
        cookie.setSecure(secure);
        cookie.setHttpOnly(isHttpOnly);
        if (path.isEmpty()) {
            cookie.setPath("/");
        } else {
            cookie.setPath(path);
        }
        if (domain != null) {
            cookie.setDomain(domain);
        }
        cookie.setComment(comment);
        resp.addCookie(cookie);
    }

    @Override
    public Optional<String> getCookie(String name) {
        return getCookie(name, req);
    }

    public static Optional<String> getCookie(String name, HttpServletRequest req) {
        return Optional.ofNullable(req.getCookies()).map(Stream::of)
                .flatMap(cookieStream -> cookieStream.filter(c -> c.getName().equalsIgnoreCase(name)).findAny())
                .map(c -> URLDecoder.decode(c.getValue(), CHARSET));
    }

    @Override
    public Optional<String> getHeader(String name) {
        return Optional.ofNullable(req.getHeader(name));
    }

    @Override
    public void sendError(int statusCode, String message) throws IOException {
        resp.sendError(statusCode, message);
    }

    @Override
    public void sendError(int statusCode) throws IOException {
        resp.sendError(statusCode);
    }

    @Override
    public boolean isUserInRole(String role) {
        return req.isUserInRole(role);
    }

    @Override
    public boolean isUserLoggedIn() {
        return req.getRemoteUser() != null;
    }

    @Override
    public void setSessionAttribute(String name, Object value, boolean invalidate) {
        if (invalidate) {
            req.getSession().invalidate();
        }
        req.getSession(true).setAttribute(name, value);
    }

    @Override
    public Optional<?> getSessionAttribute(String name, boolean createIfMissing) {
        HttpSession session = req.getSession(createIfMissing);
        return session != null
                ? Optional.ofNullable(session.getAttribute(name))
                : Optional.empty();
    }

    @Override
    public X509Certificate[] getClientCertificate() {
        return (X509Certificate[]) req.getAttribute("javax.servlet.request.X509Certificate");
    }

    @Override
    public Principal getUserPrincipal() {
        return req.getUserPrincipal();
    }

    @Override
    public void setStatus(int statusCode) {
        resp.setStatus(statusCode);
    }

    @Override
    public void authenticate() throws IOException {
        try {
            req.authenticate(resp);
        } catch (ServletException e) {
            throw ExceptionUtil.softenException(e);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + getHttpMethod() + " " + getRequestURL() + "]";
    }
}

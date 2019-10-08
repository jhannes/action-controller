package org.actioncontroller.servlet;

import org.actioncontroller.HttpActionException;
import org.actioncontroller.HttpRequestException;
import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.meta.WriterConsumer;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.reflect.Parameter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.actioncontroller.ExceptionUtil.softenException;

public class ServletHttpExchange implements ApiHttpExchange {

    public static final Charset CHARSET = StandardCharsets.ISO_8859_1;
    private final HttpServletRequest req;
    private final HttpServletResponse resp;
    private final String method;
    private Map<String, String> pathParams = new HashMap<>();

    public ServletHttpExchange(HttpServletRequest req, HttpServletResponse resp) {
        this.req = req;
        this.resp = resp;
        this.method = req.getMethod();
    }

    @Override
    public String getHttpMethod() {
        return method;
    }

    @Override
    public String getServerURL() {
        String host = Optional.ofNullable(req.getHeader("X-Forwarded-Host")).orElseGet(this::calculateHost);
        return getScheme() + "://" + host;
    }

    private String getScheme() {
        return Optional.ofNullable(req.getHeader("X-Forwarded-Proto")).orElse(req.getScheme());
    }

    private String calculateHost() {
        return req.getServerName() + (req.getServerPort() == getDefaultPort() ? "" : ":" + req.getServerPort());
    }

    private int getDefaultPort() {
        return getScheme().equals("https") ? 443 : (getScheme().equals("http") ? 80 : -1);
    }

    @Override
    public URL getContextURL() {
        return toURL(getServerURL() + req.getContextPath());
    }

    @Override
    public URL getApiURL() {
        return toURL(getServerURL() + req.getContextPath() + req.getServletPath());
    }

    private URL toURL(String s) {
        try {
            return new URL(s);
        } catch (MalformedURLException e) {
            throw softenException(e);
        }
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
    public void setResponseHeader(String key, String value) {
        resp.setHeader(key, value);
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        resp.sendRedirect(location);
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
    public Object getParameter(String name, Parameter parameter) {
        String value = req.getParameter(name);
        try {
            return ApiHttpExchange.convertTo(value, name, parameter);
        } catch (IllegalArgumentException e) {
            throw new HttpRequestException("Could not convert " + name + "=" + value + " to " + parameter.getType().getTypeName());
        }
    }

    @Override
    public boolean hasParameter(String name) {
        return req.getParameter(name) != null;
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
    public String getClientIp() {
        return Optional.ofNullable(req.getHeader("X-Forwarded-For")).orElse(req.getRemoteAddr());
    }

    @Override
    public void setCookie(String name, String value, boolean secure) {
        Cookie cookie;
        if (value == null) {
            cookie = new Cookie(name, "");
            cookie.setMaxAge(0);
        } else {
            cookie = new Cookie(name, URLEncoder.encode(value, CHARSET));
        }
        cookie.setSecure(secure);
        cookie.setPath(req.getContextPath() + req.getServletPath());
        resp.addCookie(cookie);
    }

    @Override
    public String getCookie(String name) {
        return Optional.ofNullable(req.getCookies()).map(Stream::of)
                .flatMap(cookieStream -> cookieStream.filter(c -> c.getName().equalsIgnoreCase(name)).findAny())
                .map(c -> URLDecoder.decode(c.getValue(), CHARSET))
                .orElse(null);
    }

    @Override
    public String getHeader(String name) {
        return req.getHeader(name);
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

    private String getFullURL() {
        return getServerURL() + req.getContextPath() + req.getServletPath() + getPathInfo() + (req.getQueryString() != null ? "?" + req.getQueryString() : "");
    }

    @Override
    public X509Certificate[] getClientCertificate() {
        return (X509Certificate[]) req.getAttribute("javax.servlet.request.X509Certificate");
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + method + " " + getFullURL() + "]";
    }
}

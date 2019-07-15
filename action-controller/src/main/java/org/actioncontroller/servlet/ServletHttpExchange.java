package org.actioncontroller.servlet;

import org.actioncontroller.HttpActionException;
import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.meta.WriterConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Parameter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class ServletHttpExchange implements ApiHttpExchange {
    private final static Logger logger = LoggerFactory.getLogger(ServletHttpExchange.class);

    private final HttpServletRequest req;
    private final HttpServletResponse resp;
    private Map<String, String> pathParams = new HashMap<>();

    public ServletHttpExchange(HttpServletRequest req, HttpServletResponse resp) {
        this.req = req;
        this.resp = resp;
    }

    @Override
    public void write(String contentType, WriterConsumer consumer) throws IOException {
        resp.setContentType(contentType);
        consumer.accept(resp.getWriter());
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
    public String getServerURL() {
        String host = Optional.ofNullable(req.getHeader("X-Forwarded-Host"))
                .orElseGet(() -> Optional.ofNullable(req.getHeader("Host"))
                        .orElseGet(this::calculateHost));
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
    public URL getContextURL() throws MalformedURLException {
        return new URL(getServerURL() + req.getContextPath());
    }

    @Override
    public URL getApiURL() {
        try {
            return new URL(getServerURL() + req.getContextPath() + req.getServletPath());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
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
    public Reader getReader() throws IOException {
        return req.getReader();
    }

    @Override
    public String getClientIp() {
        return Optional.ofNullable(req.getHeader("X-Forwarded-For")).orElse(req.getRemoteAddr());
    }

    @Override
    public Object getParameter(String name, Parameter parameter) {
        return ApiHttpExchange.convertTo(req.getParameter(name), name, parameter);
    }

    @Override
    public void setCookie(String name, String value, boolean secure) {
        Cookie cookie;
        if (value == null) {
            cookie = new Cookie(name, "");
            cookie.setMaxAge(0);
        } else {
            cookie = new Cookie(name, value);
        }
        cookie.setSecure(secure);
        cookie.setPath(req.getContextPath() + req.getServletPath());
        resp.addCookie(cookie);
    }

    @Override
    public Object getCookie(String name, Parameter parameter) {
        String cookie = Optional.ofNullable(req.getCookies()).map(Stream::of)
                .flatMap(cookieStream -> cookieStream.filter(c -> c.getName().equalsIgnoreCase(name)).findAny())
                .map(Cookie::getValue)
                .orElse(null);
        return ApiHttpExchange.convertTo(cookie, name, parameter);
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
    public Optional<?> getSessionAttribute(String name) {
        return Optional.empty();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + req.getMethod() + " " + getFullURL() + "]";
    }

    private String getFullURL() {
        return getApiURL() + req.getPathInfo() + (req.getQueryString() != null ? "?" + req.getQueryString() : "");
    }
}

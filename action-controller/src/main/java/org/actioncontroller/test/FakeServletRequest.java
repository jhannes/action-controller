package org.actioncontroller.test;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;
import java.io.BufferedReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * DANGER! Unfinished class! Implement methods as you go!
 */
public class FakeServletRequest implements HttpServletRequest {
    private final String scheme;
    private final int port;
    private final String host;
    private String method;
    private final String contextPath;
    private final String servletPath;
    private final String pathInfo;

    private Map<String, List<String>> headers = new HashMap<>();
    private HashMap<String, String> parameters = new HashMap<>();
    private Supplier<Reader> readerSupplier;

    /**
     * DANGER! Unfinished class! Implement methods as you go!
     */
    public FakeServletRequest(String method, URL contextRoot, String servletPath, String pathInfo) {
        this.method = method.toUpperCase();
        this.contextPath = contextRoot.getPath();
        this.servletPath = servletPath;
        this.pathInfo = pathInfo;
        this.scheme = contextRoot.getProtocol();
        this.port = contextRoot.getPort() != -1 ? contextRoot.getPort() : defaultPort(scheme);
        this.host = contextRoot.getHost();
    }

    private int defaultPort(String scheme) {
        return scheme.equals("https") ? 443 : 80;
    }

    @Override
    public String getAuthType() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public Cookie[] getCookies() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public long getDateHeader(String s) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public String getHeader(String s) {
        return Optional.ofNullable(headers.get(s)).map(l -> l.get(0)).orElse(null);
    }

    @Override
    public Enumeration<String> getHeaders(String s) {
        return Optional.ofNullable(headers.get(s)).map(Collections::enumeration).orElse(null);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return Collections.enumeration(headers.keySet());
    }

    @Override
    public int getIntHeader(String s) {
        return Optional.ofNullable(getHeader(s)).map(Integer::parseInt).orElse(-1);
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public String getPathInfo() {
        return pathInfo;
    }

    @Override
    public String getPathTranslated() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public String getContextPath() {
        return contextPath;
    }

    @Override
    public String getQueryString() {
        if (parameters.isEmpty()) {
            return null;
        } else {
            return parameters.entrySet().stream()
                    .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                    .collect(Collectors.joining("&"));
        }
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "US-ASCII");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getRemoteUser() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public boolean isUserInRole(String s) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public Principal getUserPrincipal() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public String getRequestedSessionId() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public String getRequestURI() {
        return getContextPath() + getServletPath() + getPathInfo();
    }

    @Override
    public StringBuffer getRequestURL() {
        final StringBuffer url = new StringBuffer(128);
        url.append(getScheme()).append("://").append(getRemoteHost());
        if (port > 0 && port != defaultPort(scheme)) {
            url.append(':').append(port);
        }
        url.append(getRequestURI());
        return url;
    }

    @Override
    public String getServletPath() {
        return servletPath;
    }

    @Override
    public HttpSession getSession(boolean b) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public HttpSession getSession() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public String changeSessionId() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public boolean isRequestedSessionIdFromUrl() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public boolean authenticate(HttpServletResponse httpServletResponse) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public void login(String s, String s1) {

    }

    @Override
    public void logout() {

    }

    @Override
    public Collection<Part> getParts() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public Part getPart(String s) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> aClass) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public Object getAttribute(String s) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public String getCharacterEncoding() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public void setCharacterEncoding(String s) {

    }

    @Override
    public int getContentLength() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public long getContentLengthLong() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public String getContentType() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public ServletInputStream getInputStream() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public String getParameter(String s) {
        return parameters.get(s);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public String[] getParameterValues(String s) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public String getProtocol() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public String getScheme() {
        return scheme;
    }

    @Override
    public String getServerName() {
        return host;
    }

    @Override
    public int getServerPort() {
        return port;
    }

    @Override
    public BufferedReader getReader() {
        if (readerSupplier != null) {
            return new BufferedReader(readerSupplier.get());
        } else {
            throw new AssertionError("Call setReader first");
        }
    }

    @Override
    public String getRemoteAddr() {
        return "127.0.0.1";
    }

    @Override
    public String getRemoteHost() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public void setAttribute(String s, Object o) {

    }

    @Override
    public void removeAttribute(String s) {

    }

    @Override
    public Locale getLocale() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public Enumeration<Locale> getLocales() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public boolean isSecure() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String s) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public String getRealPath(String s) {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public int getRemotePort() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public String getLocalName() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public String getLocalAddr() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public int getLocalPort() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public ServletContext getServletContext() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public boolean isAsyncStarted() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public boolean isAsyncSupported() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public AsyncContext getAsyncContext() {
        throw new AssertionError("called unexpected method");
    }

    @Override
    public DispatcherType getDispatcherType() {
        throw new AssertionError("called unexpected method");
    }

    public void setParameter(String key, String value) {
        parameters.put(key, value);
    }

    public void setReader(Supplier<Reader> readerSupplier) {
        this.readerSupplier = readerSupplier;
    }
}

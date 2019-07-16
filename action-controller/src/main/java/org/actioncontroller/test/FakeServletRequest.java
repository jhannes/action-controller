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
import java.util.ArrayList;
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
    private String pathInfo;

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
        throw unimplemented("called unexpected method");
    }

    private List<Cookie> cookies = new ArrayList<>();

    public void setCookie(String key, String value) {
        cookies.add(new Cookie(key, value));
    }

    @Override
    public Cookie[] getCookies() {
        return cookies.isEmpty() ? null : cookies.toArray(new Cookie[0]);
    }

    @Override
    public long getDateHeader(String s) {
        throw unimplemented("called unexpected method");
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

    public void setPathInfo(String pathInfo) {
        this.pathInfo = pathInfo;
    }

    @Override
    public String getPathTranslated() {
        throw unimplemented("called unexpected method");
    }

    private AssertionError unimplemented(String s) {
        return new AssertionError(s);
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
        throw unimplemented("called unexpected method");
    }

    @Override
    public boolean isUserInRole(String s) {
        throw unimplemented("called unexpected method");
    }

    @Override
    public Principal getUserPrincipal() {
        throw unimplemented("called unexpected method");
    }

    @Override
    public String getRequestedSessionId() {
        throw unimplemented("called unexpected method");
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
        throw unimplemented("called unexpected method");
    }

    @Override
    public HttpSession getSession() {
        throw unimplemented("called unexpected method");
    }

    @Override
    public String changeSessionId() {
        throw unimplemented("called unexpected method");
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        throw unimplemented("called unexpected method");
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        throw unimplemented("called unexpected method");
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        throw unimplemented("called unexpected method");
    }

    @Override
    public boolean isRequestedSessionIdFromUrl() {
        throw unimplemented("called unexpected method");
    }

    @Override
    public boolean authenticate(HttpServletResponse httpServletResponse) {
        throw unimplemented("called unexpected method");
    }

    @Override
    public void login(String s, String s1) {

    }

    @Override
    public void logout() {

    }

    @Override
    public Collection<Part> getParts() {
        throw unimplemented("called unexpected method");
    }

    @Override
    public Part getPart(String s) {
        throw unimplemented("called unexpected method");
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> aClass) {
        throw unimplemented("called unexpected method");
    }

    @Override
    public Object getAttribute(String s) {
        throw unimplemented("called unexpected method");
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        throw unimplemented("called unexpected method");
    }

    @Override
    public String getCharacterEncoding() {
        // TODO
        throw unimplemented("called unexpected method");
    }

    @Override
    public void setCharacterEncoding(String s) {
        // TODO

    }

    @Override
    public int getContentLength() {
        // TODO
        throw unimplemented("called unexpected method");
    }

    @Override
    public long getContentLengthLong() {
        throw unimplemented("called unexpected method");
    }

    @Override
    public String getContentType() {
        // TODO
        throw unimplemented("called unexpected method");
    }

    @Override
    public ServletInputStream getInputStream() {
        // TODO
        throw unimplemented("called unexpected method");
    }

    @Override
    public String getParameter(String s) {
        return parameters.get(s);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        // TODO
        throw unimplemented("called unexpected method");
    }

    @Override
    public String[] getParameterValues(String s) {
        // TODO
        throw unimplemented("called unexpected method");
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        // TODO
        throw unimplemented("called unexpected method");
    }

    @Override
    public String getProtocol() {
        // TODO
        throw unimplemented("called unexpected method");
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
            // TODO
            throw unimplemented("Call setReader first");
        }
    }

    @Override
    public String getRemoteAddr() {
        return "127.0.0.1";
    }

    @Override
    public String getRemoteHost() {
        return host;
    }

    @Override
    public void setAttribute(String s, Object o) {

    }

    @Override
    public void removeAttribute(String s) {

    }

    @Override
    public Locale getLocale() {
        // TODO
        throw unimplemented("called unexpected method");
    }

    @Override
    public Enumeration<Locale> getLocales() {
        // TODO
        throw unimplemented("called unexpected method");
    }

    @Override
    public boolean isSecure() {
        // TODO
        throw unimplemented("called unexpected method");
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String s) {
        throw unimplemented("called unexpected method");
    }

    @Override
    public String getRealPath(String s) {
        throw unimplemented("called unexpected method");
    }

    @Override
    public int getRemotePort() {
        throw unimplemented("called unexpected method");
    }

    @Override
    public String getLocalName() {
        throw unimplemented("called unexpected method");
    }

    @Override
    public String getLocalAddr() {
        // TODO
        throw unimplemented("called unexpected method");
    }

    @Override
    public int getLocalPort() {
        throw unimplemented("called unexpected method");
    }

    @Override
    public ServletContext getServletContext() {
        throw unimplemented("called unexpected method");
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        throw unimplemented("called unexpected method");
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
        throw unimplemented("called unexpected method");
    }

    @Override
    public boolean isAsyncStarted() {
        throw unimplemented("called unexpected method");
    }

    @Override
    public boolean isAsyncSupported() {
        throw unimplemented("called unexpected method");
    }

    @Override
    public AsyncContext getAsyncContext() {
        throw unimplemented("called unexpected method");
    }

    @Override
    public DispatcherType getDispatcherType() {
        throw unimplemented("called unexpected method");
    }

    public void setParameter(String key, String value) {
        parameters.put(key, value);
    }

    public void setReader(Supplier<Reader> readerSupplier) {
        this.readerSupplier = readerSupplier;
    }
}

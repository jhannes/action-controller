package org.fakeservlet;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.ReadListener;
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
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import java.util.Vector;
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
    private Map<String, String> parameters = new HashMap<>();
    private FakeHttpSession httpSession;
    private Map<String, Object> attributes = new HashMap<>();
    private byte[] requestBody;
    private Principal userPrincipal;

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
        throw unimplemented();
    }

    private List<Cookie> cookies = new ArrayList<>();

    public void setCookie(String key, String value) {
        cookies.add(new Cookie(key, value));
    }

    @Override
    public Cookie[] getCookies() {
        return cookies.isEmpty() ? null : cookies.toArray(new Cookie[0]);
    }

    public void setCookies(List<Cookie> cookies) {
        this.cookies = cookies;
    }

    @Override
    public long getDateHeader(String s) {
        throw unimplemented();
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
        throw unimplemented();
    }

    private AssertionError unimplemented() {
        return new AssertionError("called unexpected method");
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
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    @Override
    public String getRemoteUser() {
        return userPrincipal != null ? userPrincipal.getName() : null;
    }

    @Override
    public boolean isUserInRole(String s) {
        throw unimplemented();
    }

    @Override
    public Principal getUserPrincipal() {
        return userPrincipal;
    }

    @Override
    public String getRequestedSessionId() {
        throw unimplemented();
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
    public FakeHttpSession getSession(boolean create) {
        if (httpSession == null && create) {
            httpSession = new FakeHttpSession();
        }
        return httpSession;
    }

    @Override
    public HttpSession getSession() {
        return getSession(true);
    }

    @Override
    public String changeSessionId() {
        throw unimplemented();
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        throw unimplemented();
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        throw unimplemented();
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        throw unimplemented();
    }

    @Override
    public boolean isRequestedSessionIdFromUrl() {
        throw unimplemented();
    }

    @Override
    public boolean authenticate(HttpServletResponse response) {
        response.setStatus(401);
        return false;
    }

    @Override
    public void login(String s, String s1) {

    }

    @Override
    public void logout() {

    }

    @Override
    public Collection<Part> getParts() {
        throw unimplemented();
    }

    @Override
    public Part getPart(String s) {
        throw unimplemented();
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> aClass) {
        throw unimplemented();
    }

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return new Vector<>(attributes.keySet()).elements();
    }

    @Override
    public String getCharacterEncoding() {
        // TODO
        throw unimplemented();
    }

    @Override
    public void setCharacterEncoding(String s) {
        // TODO

    }

    @Override
    public int getContentLength() {
        // TODO
        throw unimplemented();
    }

    @Override
    public long getContentLengthLong() {
        throw unimplemented();
    }

    @Override
    public String getContentType() {
        // TODO
        throw unimplemented();
    }

    @Override
    public ServletInputStream getInputStream() {
        if (requestBody != null) {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(requestBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return inputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // TODO
                    throw unimplemented();
                }

                @Override
                public int read() {
                    return inputStream.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return inputStream.read(b, off, len);
                }
            };
        } else {
            throw new AssertionError("Call setRequestBody first");
        }
    }

    @Override
    public String getParameter(String s) {
        return parameters.get(s);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return new Vector<>(parameters.keySet()).elements();
    }

    @Override
    public String[] getParameterValues(String s) {
        return getParameter(s) != null ? new String[] { getParameter(s) } : null;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        HashMap<String, String[]> map = new HashMap<>();
        parameters.forEach((k,v) -> map.put(k, new String[] { v }));
        return map;
    }

    @Override
    public String getProtocol() {
        // TODO
        throw unimplemented();
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
        return new BufferedReader(new InputStreamReader(getInputStream()));
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
    public void setAttribute(String name, Object o) {
        this.attributes.put(name, o);
    }

    @Override
    public void removeAttribute(String name) {
        this.attributes.remove(name);
    }

    @Override
    public Locale getLocale() {
        // TODO
        throw unimplemented();
    }

    @Override
    public Enumeration<Locale> getLocales() {
        // TODO
        throw unimplemented();
    }

    @Override
    public boolean isSecure() {
        // TODO
        throw unimplemented();
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String s) {
        throw unimplemented();
    }

    @Override
    public String getRealPath(String s) {
        throw unimplemented();
    }

    @Override
    public int getRemotePort() {
        throw unimplemented();
    }

    @Override
    public String getLocalName() {
        throw unimplemented();
    }

    @Override
    public String getLocalAddr() {
        // TODO
        throw unimplemented();
    }

    @Override
    public int getLocalPort() {
        throw unimplemented();
    }

    @Override
    public ServletContext getServletContext() {
        throw unimplemented();
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        throw unimplemented();
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
        throw unimplemented();
    }

    @Override
    public boolean isAsyncStarted() {
        throw unimplemented();
    }

    @Override
    public boolean isAsyncSupported() {
        throw unimplemented();
    }

    @Override
    public AsyncContext getAsyncContext() {
        throw unimplemented();
    }

    @Override
    public DispatcherType getDispatcherType() {
        throw unimplemented();
    }

    public void setParameter(String key, String value) {
        parameters.put(key, value);
    }

    public void setSession(FakeHttpSession httpSession) {
        this.httpSession = httpSession;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void addHeader(String name, String value) {
        headers.computeIfAbsent(name, n -> new ArrayList<>()).add(value);
    }

    public void setRequestBody(byte[] requestBody) {
        this.requestBody = requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody.getBytes();
    }

    public void setUserPrincipal(Principal userPrincipal) {
        this.userPrincipal = userPrincipal;
    }
}

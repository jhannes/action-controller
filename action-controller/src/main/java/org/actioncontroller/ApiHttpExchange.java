package org.actioncontroller;

import org.actioncontroller.exceptions.HttpActionException;
import org.actioncontroller.exceptions.HttpRequestException;
import org.actioncontroller.exceptions.HttpServerErrorException;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpReturnMapping;
import org.actioncontroller.meta.OutputStreamConsumer;
import org.actioncontroller.meta.WriterConsumer;
import org.actioncontroller.util.IOUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URL;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Abstracts the interaction with the http request and response. This interface
 * acts as an abstraction that enables Action Controller to work both with
 * both the servlet API and {@link com.sun.net.httpserver.HttpServer}.
 * {@link ApiHttpExchange} also provides convenience methods used by several
 * {@link HttpParameterMapper} and {@link HttpReturnMapping} implementations.
 */
public interface ApiHttpExchange {

    String getHttpMethod();

    /**
     * Returns the context path of the server. Blank if the server context is the root of the server
     */
    String getContextPath();

    /**
     * Returns the scheme, hostname and port part of the requesting URL, for example
     * <code>http://www.example.com</code> or <code>https://localhost:8443</code>
     */
    String getServerURL();

    /**
     * Returns the scheme, hostname, port and context path of the server
     */
    URL getContextURL();

    /**
     * Returns the path to where the controllers paths are evaluated relative to, that
     * is, the Servlet's context URL. For example, if an {@link org.actioncontroller.servlet.ApiServlet}
     * is bound as "/api/*" in a webapp mounted at "/app", getApiURL might return
     * <code>https://example.com:7443/app/api</code>.
     */
    URL getApiURL();

    /**
     * Returns the part of the URL after getApiURL. For example if a controller is mounted at
     * <code>https://example.com:7443/app/api</code> and the client requests GET
     * <code>https://example.com:7443/app/api/hello/world</code> getPathInfo returns <code>"/hello/world"</code>
     */
    String getPathInfo();

    default String getRequestURL() {
        return getApiURL() + getPathInfo() + (getQueryString() != null ? "?" + getQueryString() : "");
    }

    /**
     * Returns the query string of the requested url. For example if the client requests
     * GET <code>https://example.com:7443/app/api/hello/world?greeting=Hello+There</code>
     * <code>getQueryString()</code> returns <code>greeting=Hello+There</code>.
     */
    String getQueryString();

    /**
     * Returns the specified query string of the requested url for a get request or x-www-form-urlencoded body
     * parameter for a POST or PUT request. For example if the client requests
     * GET <code>https://example.com:7443/app/api/hello/world?greeting=Hello+There&amp;greeting=Hi</code>
     * <code>getParameter("greeting")</code> returns <code>["Hello There", "Hi"]</code>.
     *
     * @param name The query parameter name
     * @return the non-empty list of parameters matching the name or null if none were provided
     */
    List<String> getParameters(String name);

    default boolean hasParameter(String name) {
        return getParameters(name) != null;
    }

    void write(String contentType, WriterConsumer consumer) throws IOException;

    void output(String contentType, OutputStreamConsumer consumer) throws IOException;

    Optional<String> getHeader(String name);

    /**
     * Returns true if the "Accept" request header matches the argument content type
     */
    default boolean accept(String contentType) {
        return getHeader("Accept")
                .map(acceptHeader -> Stream.of(acceptHeader.split(",")).anyMatch(type -> type.startsWith(contentType)))
                .orElse(false);

    }

    String getClientIp();

    void setResponseHeader(String key, String value);

    void sendRedirect(String path) throws IOException;

    /**
     * @throws HttpActionException throws 500 if the name was not matched with a path parameter
     */
    String pathParam(String name) throws HttpActionException;

    void setPathParameters(Map<String, String> pathParameters);

    Reader getReader() throws IOException;

    InputStream getInputStream() throws IOException;

    /**
     * Sets a cookie with default values. If value == null, deletes the cookie
     */
    default void setCookie(String name, String value) {
        setCookie(name, value, true, true);
    }

    /**
     * Sets a cookie with default values. If value == null, deletes the cookie
     */
    default void setCookie(String name, String value, boolean secure, boolean isHttpOnly) {
        setCookie(name, value, secure, isHttpOnly, getContextPath(), -1, null, null);
    }

    /**
     * Sets a cookie with default values. If value == null, deletes the cookie
     */
    void setCookie(String name, String value, boolean secure, boolean isHttpOnly, String contextPath, int maxAge, String domain, String comment);

    Optional<String> getCookie(String name);

    void sendError(int statusCode, String message) throws IOException;

    void sendError(int statusCode) throws IOException;

    /** Sets the http status code, but don't trigger the error handler (servlets) */
    void setStatus(int statusCode) throws IOException;

    boolean isUserLoggedIn();

    boolean isUserInRole(String role);

    void setSessionAttribute(String name, Object value, boolean invalidate);

    @SuppressWarnings("rawtypes")
    Optional getSessionAttribute(String name, boolean createIfMissing);
    
    @SuppressWarnings("rawtypes")
    default Optional getSessionAttribute(String name) {
        return getSessionAttribute(name, false);
    }

    @SuppressWarnings("unchecked")
    default <T> Optional<T> getSessionAttribute(Class<T> name, boolean createIfMissing) {
        return (Optional<T>) getSessionAttribute(name.getName(), createIfMissing);
    }

    default <T> Optional<T> getSessionAttribute(Class<T> name) {
        return getSessionAttribute(name, false);
    }

    X509Certificate[] getClientCertificate();

    Principal getUserPrincipal();

    void authenticate() throws IOException;
}

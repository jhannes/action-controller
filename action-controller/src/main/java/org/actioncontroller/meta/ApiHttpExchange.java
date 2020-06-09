package org.actioncontroller.meta;

import org.actioncontroller.HttpActionException;
import org.actioncontroller.HttpRequestException;
import org.actioncontroller.IOUtil;
import org.actioncontroller.TypesUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.net.URL;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
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
     * Returns the scheme, hostname and port part of the requesting URL, for example
     * <code>http://www.example.com</code> or <code>https://localhost:8443</code>
     */
    String getServerURL();

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

    /**
     * Returns the whole URL, including query parameter. For example if the client requests
     * GET <code>https://example.com:7443/app/api/hello/world?greeting=Hello+There</code>
     * <code>getRequestURL()</code> returns <code>https://example.com:7443/app/api/hello/world?greeting=Hello+There</code>.
     */
    String getRequestURL();

    /**
     * Returns the query string of the requested url. For example if the client requests
     * GET <code>https://example.com:7443/app/api/hello/world?greeting=Hello+There</code>
     * <code>getQueryString()</code> returns <code>greeting=Hello+There</code>.
     */
    String getQueryString();

    /**
     * Returns the specified query string of the requested url for a get request or x-www-form-urlencoded body
     * parameter for a POST or PUT request. For example if the client requests
     * GET <code>https://example.com:7443/app/api/hello/world?greeting=Hello+There</code>
     * <code>getParameter("greeting", ...)</code> returns <code>Hello There</code>.
     *
     * @param name The query parameter name.
     * @param parameter The method parameter that this will be mapped to. Will be used to convert the value using {@link #convertTo}
     */
    Object getParameter(String name, Parameter parameter);

    String getParameter(String name);

    boolean hasParameter(String name);

    void write(String contentType, WriterConsumer consumer) throws IOException;

    void output(String contentType, OutputStreamConsumer consumer) throws IOException;

    String getHeader(String name);

    /**
     * Returns true if the "Accept" request header matches the argument content type
     */
    default boolean accept(String contentType) {
        return Optional.ofNullable(getHeader("Accept"))
                .map(acceptHeader -> Stream.of(acceptHeader.split(",")).anyMatch(type -> type.startsWith(contentType)))
                .orElse(false);

    }

    String getClientIp();

    void setResponseHeader(String key, String value);

    void sendRedirect(String path) throws IOException;

    /**
     * @throws HttpActionException throws 500 if the name was not matched with a path parameter
     */
    Object pathParam(String name, Parameter parameter) throws HttpActionException;

    void setPathParameters(Map<String, String> pathParameters);

    Reader getReader() throws IOException;

    InputStream getInputStream() throws IOException;

    /**
     * Sets a cookie with default values. If value == null, deletes the cookie
     */
    void setCookie(String name, String value, boolean secure, boolean isHttpOnly);

    String getCookie(String name);

    void sendError(int statusCode, String message) throws IOException;

    void sendError(int statusCode) throws IOException;

    boolean isUserLoggedIn();

    boolean isUserInRole(String role);

    void setSessionAttribute(String name, Object value, boolean invalidate);

    Optional getSessionAttribute(String name, boolean createIfMissing);

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

    @SuppressWarnings("unchecked")
    static Object convertParameterType(String value, Type parameterType) {
        if (value == null) {
            return null;
        }
        if (parameterType == String.class) {
            return value;
        } else if (parameterType == Boolean.class || parameterType == Boolean.TYPE) {
            return Boolean.parseBoolean(value);
        } else if (parameterType == Integer.class || parameterType == Integer.TYPE) {
            return Integer.parseInt(value);
        } else if (parameterType == UUID.class) {
            return UUID.fromString(value);
        } else if (parameterType == Long.class || parameterType == Long.TYPE) {
            return Long.parseLong(value);
        } else if (Enum.class.isAssignableFrom((Class<?>)parameterType)) {
            return Enum.valueOf((Class) parameterType, value);
        } else if (URL.class.isAssignableFrom((Class<?>)parameterType)) {
            return IOUtil.asURL(value);
        } else {
            throw new HttpActionException(500, "Unhandled parameter type " + parameterType);
        }
    }

    static HttpParameterMapper withOptional(Parameter parameter, HttpParameterMapper innerMapping) {
        if (parameter.getType() == Optional.class) {
            return exchange -> Optional.ofNullable(innerMapping.apply(exchange));
        } else {
            return exchange -> Optional.ofNullable(innerMapping.apply(exchange))
                    .orElseThrow(() -> new HttpRequestException("Missing required parameter value"));
        }
    }

    /**
     * Converts the parameter value to the type specified by the parameter. Supports String, int, (long), (short), (byte),
     * double, (float), UUID, (Instant), (LocalDate) and enums, as well as Optionals of the same.
     * @param value The string value read from the http value
     * @param parameterName Used for exception messages
     * @param parameter the Parameter object from the method that this value should be mapped to. Needed to deal with optionals
     * @return The value converted to a type compatible with parameter
     * @throws HttpRequestException if the value is null and the parameter is not Optional
     * @throws HttpRequestException if the value doesn't have a legal representation in the target type
     */
    static Object convertTo(String value, String parameterName, Parameter parameter) {
        boolean optional = parameter.getType() == Optional.class;
        if (value == null) {
            if (!optional) {
                throw new HttpRequestException("Missing required parameter " + parameterName);
            }
            return Optional.empty();
        } else if (optional) {
            return Optional.of(convertParameterType(value, TypesUtil.typeParameter(parameter.getParameterizedType())));
        } else {
            return convertParameterType(value, parameter.getType());
        }
    }

    static Type getTargetType(Parameter parameter) {
        if (parameter.getType() == Consumer.class) {
            return TypesUtil.typeParameter(parameter.getParameterizedType());
        } else if (parameter.getType() == Optional.class) {
            return TypesUtil.typeParameter(parameter.getParameterizedType());
        } else {
            return parameter.getType();
        }
    }

    X509Certificate[] getClientCertificate();

    Principal getUserPrincipal();

    void authenticate() throws IOException;
}

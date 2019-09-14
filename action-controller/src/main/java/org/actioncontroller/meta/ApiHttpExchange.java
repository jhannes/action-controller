package org.actioncontroller.meta;

import org.actioncontroller.HttpActionException;
import org.actioncontroller.HttpRequestException;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

    URL getContextURL() throws MalformedURLException;

    /**
     * Returns the path to where the controllers paths are evaluated relative to, that
     * is, the Servlet's context URL. For example, if an {@link org.actioncontroller.servlet.ApiServlet}
     * is bound as "/api/*" in a webapp mounted at "/app", getApiURL might return
     * <code>https://example.com:7443/app/api</code>.
     */
    URL getApiURL() throws MalformedURLException;

    /**
     * Returns the part of the URL after getApiURL. For example if a controller is mounted at
     * <code>https://example.com:7443/app/api</code> and the client requests GET
     * <code>https://example.com:7443/app/api/hello/world</code> getPathInfo returns <code>"/hello/world"</code>
     */
    String getPathInfo();

    void write(String contentType, WriterConsumer consumer) throws IOException;

    String getHeader(String name);

    String getClientIp();

    void setResponseHeader(String key, String value);

    void sendRedirect(String path) throws IOException;

    /**
     * @throws HttpActionException throws 500 if the name was not matched with a path parameter
     */
    Object pathParam(String name, Parameter parameter) throws HttpActionException;

    void setPathParameters(Map<String, String> pathParameters);

    Reader getReader() throws IOException;

    Object getParameter(String name, Parameter parameter);

    void setCookie(String name, String value, boolean secure);

    Object getCookie(String name, Parameter parameter);

    void sendError(int statusCode, String message) throws IOException;

    void sendError(int statusCode) throws IOException;

    boolean isUserLoggedIn();

    boolean isUserInRole(String role);

    void setSessionAttribute(String name, Object value, boolean invalidate);

    Optional getSessionAttribute(String name, boolean createIfMissing);

    static Object convertParameterType(String value, Type parameterType) {
        if (parameterType == String.class) {
            return value;
        } else if (parameterType == Boolean.class) {
            return Boolean.parseBoolean(value);
        } else if (parameterType == Integer.class || parameterType == Integer.TYPE) {
            return Integer.parseInt(value);
        } else if (parameterType == UUID.class) {
            return UUID.fromString(value);
        } else if (parameterType == Long.class || parameterType == Long.TYPE) {
            return Long.parseLong(value);
        } else if (Enum.class.isAssignableFrom((Class<?>)parameterType)) {
            return Enum.valueOf((Class) parameterType, value);
        } else {
            throw new HttpActionException(500, "Unhandled parameter type " + parameterType);
        }
    }

    static Object convertTo(String value, String parameterName, Parameter parameter) {
        boolean optional = parameter.getType() == Optional.class;

        if (value == null) {
            if (!optional) {
                throw new HttpRequestException("Missing required parameter " + parameterName);
            }
            return Optional.empty();
        }

        Type parameterType;
        if (optional) {
            Type parameterizedType = parameter.getParameterizedType();
            parameterType = ((ParameterizedType)parameterizedType).getActualTypeArguments()[0];
        } else {
            parameterType = parameter.getType();
        }

        Object parameterValue = convertParameterType(value, parameterType);
        return optional ? Optional.of(parameterValue) : parameterValue;
    }

    void calculatePathParams(String[] pathPattern);
}

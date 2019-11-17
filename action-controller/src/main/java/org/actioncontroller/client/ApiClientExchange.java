package org.actioncontroller.client;

import org.actioncontroller.meta.OutputStreamConsumer;
import org.actioncontroller.meta.WriterConsumer;

import java.io.IOException;
import java.net.URL;
import java.security.cert.X509Certificate;

/**
 * Abstracts a HTTP request and response from the client perspective. Can be implemented with various
 * HTTP clients and with fake Servlet classes.
 *
 * <h2>Usage example</h2>
 *
 * <pre>
 * exchange.setTarget("GET", "/index");
 * exchange.setRequestParameter("foo", "bar");
 *
 * exchange.execute();
 *
 * exchange.getResponseBody()
 * </pre>
 */
public interface ApiClientExchange {
    void setTarget(String method, String pathInfo);

    String getRequestMethod();

    String getPathInfo();

    void setPathInfo(String pathInfo);

    URL getRequestURL();

    /**
     * Sets the specified HTTP request parameter to be included in URL query (for GET requests) or
     * application/x-www-form-urlencoded body (for other request methods).
     *
     * @param name Name of the HTTP request parameter to set
     * @param value If null or Optional.empty, parameter will be unset; if Optional, value will be extracted
     */
    void setRequestParameter(String name, Object value);

    /**
     * Sets the specified HTTP cookie value
     *
     * @param name Name of the HTTP cookie to set
     * @param value If null or Optional.empty, cookie will be unset; if Optional, value will be extracted
     */
    void addRequestCookie(String name, Object value);

    void setHeader(String name, Object value);

    void executeRequest() throws IOException;

    int getResponseCode() throws IOException;

    String getResponseHeader(String name);

    String getResponseCookie(String name);

    String getResponseBody() throws IOException;

    byte[] getResponseBodyBytes() throws IOException;

    void checkForError() throws HttpClientException, IOException;

    void setClientCertificate(X509Certificate[] certificate);

    void write(String contentType, WriterConsumer consumer) throws IOException;

    void output(String contentType, OutputStreamConsumer consumer) throws IOException;
}

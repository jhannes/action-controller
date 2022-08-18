package org.actioncontroller.client;

import org.actioncontroller.ApiHttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.List;

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
     * Returns the path to where the controllers paths are evaluated relative to, that
     * is, the Servlet's context URL. For example, if an {@link org.actioncontroller.servlet.ApiServlet}
     * is bound as "/api/*" in a webapp mounted at "/app", getApiURL might return
     * <code>https://example.com:7443/app/api</code>.
     */
    String getApiURL();

    /**
     * Sets the specified HTTP cookie value. The cookie value will be URL encoded
     * before sent in the Cookie-header
     *
     * @param name Name of the HTTP cookie to set
     * @param value If null or Optional.empty, cookie will be unset; if Optional, value will be extracted
     */
    void addRequestCookie(String name, Object value);

    void setHeader(String name, Object value);

    void executeRequest() throws IOException;

    int getResponseCode() throws IOException;

    List<String> getResponseHeaders(String name);

    List<String> getResponseCookies(String name);

    Reader getResponseBodyReader() throws IOException;

    InputStream getResponseBodyStream() throws IOException;

    void checkForError() throws HttpClientException, IOException;

    void setClientCertificate(X509Certificate[] certificate);

    void write(String contentType, ApiHttpExchange.WriterConsumer consumer) throws IOException;

    void output(String contentType, ApiHttpExchange.OutputStreamConsumer consumer) throws IOException;

}

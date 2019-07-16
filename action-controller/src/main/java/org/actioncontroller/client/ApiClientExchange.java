package org.actioncontroller.client;

import java.io.IOException;
import java.net.URL;

public interface ApiClientExchange {
    void setTarget(String method, String pathInfo);

    String getRequestMethod();

    String getPathInfo();

    void setPathInfo(String pathInfo);

    URL getRequestURL();

    void setRequestParameter(String name, String value);

    void addRequestCookie(String name, String value);

    void executeRequest() throws IOException;

    int getResponseCode() throws IOException;

    String getResponseMessage() throws IOException;

    String getResponseHeader(String name);

    String getResponseCookie(String name);

    String getResponseBody() throws IOException;

}

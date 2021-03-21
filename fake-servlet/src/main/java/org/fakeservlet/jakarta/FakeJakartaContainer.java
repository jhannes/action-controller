package org.fakeservlet.jakarta;

import java.net.MalformedURLException;
import java.net.URL;

public class FakeJakartaContainer {
    private final URL contextRoot;
    private final String servletPath;

    public FakeJakartaContainer(URL contextRoot, String servletPath) {
        this.contextRoot = contextRoot;
        this.servletPath = servletPath;
    }

    public FakeJakartaContainer(String contextRoot, String servletPath) {
        this(asURL(contextRoot), servletPath);
    }

    private static URL asURL(String contextRoot) {
        try {
            return new URL(contextRoot);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public FakeJakartaRequest newRequest(String method, String pathInfo) {
        return new FakeJakartaRequest(method, contextRoot, servletPath, pathInfo);
    }

    public String getServletPath() {
        return contextRoot + servletPath;
    }

    public URL getContextRoot() {
        return contextRoot;
    }
}

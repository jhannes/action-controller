package org.fakeservlet;

import java.net.MalformedURLException;
import java.net.URL;

public class FakeServletContainer {
    private final URL contextRoot;
    private final String servletPath;

    public FakeServletContainer(URL contextRoot, String servletPath) {
        this.contextRoot = contextRoot;
        this.servletPath = servletPath;
    }

    public FakeServletContainer(String contextRoot, String servletPath) {
        this(asURL(contextRoot), servletPath);
    }

    private static URL asURL(String contextRoot) {
        try {
            return new URL(contextRoot);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public FakeServletRequest newRequest(String method, String pathInfo) {
        return new FakeServletRequest(method, contextRoot, servletPath, pathInfo);
    }

    public String getServletPath() {
        return contextRoot + servletPath;
    }

    public URL getContextRoot() {
        return contextRoot;
    }
}

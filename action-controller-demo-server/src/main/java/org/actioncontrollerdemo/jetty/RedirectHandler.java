package org.actioncontrollerdemo.jetty;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class RedirectHandler extends AbstractHandler {
    private String contextPath;
    private String targetPath;

    public RedirectHandler(String contextPath, String targetPath) {
        this.contextPath = contextPath;
        this.targetPath = targetPath;
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (target.equals(contextPath)) {
            response.sendRedirect(targetPath);
        }
    }
}

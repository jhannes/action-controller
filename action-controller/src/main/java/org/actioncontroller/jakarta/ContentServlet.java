package org.actioncontroller.jakarta;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.actioncontroller.content.Content;
import org.actioncontroller.content.ContentSource;

import java.io.IOException;

public class ContentServlet extends HttpServlet {

    private final ContentSource contentSource;

    public ContentServlet(String resourceBase) {
        this(ContentSource.fromClasspath(resourceBase));
    }

    public ContentServlet(ContentSource contentSource) {
        this.contentSource = contentSource;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Content content = contentSource.getContent(req.getPathInfo());
        if (content == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        } else if (content.notModified(req.getHeader("If-Modified-Since"))) {
            resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        } else {
            content.getContentType().ifPresent(resp::setContentType);
            content.getCacheControl().ifPresent(header -> resp.setHeader("Cache-Control", header));
            resp.setHeader("Last-Modified", content.getLastModifiedAsRfc1123());
            byte[] data = content.readContent();
            resp.setContentLength(data.length);
            resp.getOutputStream().write(data);
        }
    }
}

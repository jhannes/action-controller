package org.actioncontrollerdemo.servlet;

import org.actioncontrollerdemo.ContentSource;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

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
        try {
            URL resource = contentSource.resolve(req.getPathInfo().substring(1));
            Long lastModified = contentSource.lastModified(resource);
            if (lastModified != null) {
                resp.setDateHeader("Last-Modified", lastModified);

                long ifModifiedSinceHeader = req.getDateHeader("If-Modified-Since");
                if (ifModifiedSinceHeader > 0 && lastModified >= ifModifiedSinceHeader) {
                    resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    return;
                }
            }
            InputStream inputStream = resource.openStream();
            resp.setContentType(getServletContext().getMimeType(resource.getFile()));
            inputStream.transferTo(resp.getOutputStream());
        } catch (FileNotFoundException ignored) {
            resp.sendError(404);
        }
    }
}

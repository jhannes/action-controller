package org.actioncontrollerdemo.servlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;

public class ContentServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        InputStream resource = getServletContext().getResourceAsStream(req.getPathInfo());
        if (resource != null) {
            resp.setContentType(getServletContext().getMimeType(req.getPathInfo()));
            int c;
            while ((c = resource.read()) != -1) {
                resp.getOutputStream().write((byte)c);
            }
            resp.getOutputStream().flush();
        } else {
            resp.sendError(404);
        }
    }
}

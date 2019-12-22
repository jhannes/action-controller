package org.actioncontrollerdemo.servlet;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

public class ContentServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        URL baseResource = getClass().getResource("/webapp-actioncontrollerdemo/");

        if (baseResource.toString().contains("target/classes")) {
            URL sourceResources = new URL(baseResource.toString().replaceAll("target/classes", "src/main/resources"));
            try {
                sourceResources.openStream();
                baseResource = sourceResources;
            } catch (FileNotFoundException ignored) {
            }
        }

        List<String> welcomeFiles = Collections.singletonList("index.html");

        URL resource = new URL(baseResource, req.getPathInfo().substring(1));
        if (!isDirectory(resource)) {
            resp.setContentType(getServletContext().getMimeType(req.getPathInfo()));
            resource.openStream().transferTo(resp.getOutputStream());
        } else {
            for (String welcomeFile : welcomeFiles) {
                try {
                    InputStream inputStream = new URL(resource, welcomeFile).openStream();
                    resp.setContentType(getServletContext().getMimeType(welcomeFile));
                    inputStream.transferTo(resp.getOutputStream());
                    return;
                } catch (FileNotFoundException ignored) {
                }
            }
            try {
                resp.setContentType(getServletContext().getMimeType(req.getPathInfo()));
                resource.openStream().transferTo(resp.getOutputStream());
            } catch (IOException e) {
                resp.sendError(404);
            }
        }
    }

    private boolean isDirectory(URL resource) {
        if (resource.getProtocol().equals("file")) {
            try {
                Path path = Paths.get(resource.toURI());
                return Files.isDirectory(path);
            } catch (URISyntaxException ignored) {
            }
        }
        return resource.toString().endsWith("/");
    }
}

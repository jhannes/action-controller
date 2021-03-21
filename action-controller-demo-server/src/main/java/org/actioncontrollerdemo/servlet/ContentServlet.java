package org.actioncontrollerdemo.servlet;

import org.actioncontroller.ExceptionUtil;

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

    private final URL baseResource;

    public ContentServlet(String resourceBase) {
        // TODO: If resourceBase doesn't start with /, things go wrong
        // TODO: If resourceBase doesn't end with /, parent directory is used
        URL baseResourceTmp = getClass().getResource(resourceBase);
        if (baseResourceTmp == null) {
            throw new IllegalArgumentException("Could not find resource " + resourceBase);
        }
        if (baseResourceTmp.toString().contains("target/classes")) {
            try {
                URL sourceResources = new URL(baseResourceTmp.toString().replaceAll("target/classes", "src/main/resources"));
                sourceResources.openStream();
                baseResourceTmp = sourceResources;
            } catch (FileNotFoundException ignored) {
            } catch (IOException e) {
                throw ExceptionUtil.softenException(e);
            }
        }
        baseResource = baseResourceTmp;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        URL resource = new URL(baseResource, req.getPathInfo().substring(1));
        if (isDirectory(resource)) {
            List<String> welcomeFiles = Collections.singletonList("index.html");
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
                resource.openStream().transferTo(resp.getOutputStream());
            } catch (IOException e) {
                resp.sendError(404);
            }
        } else {
            try {
                resp.setContentType(getServletContext().getMimeType(req.getPathInfo()));
                resource.openStream().transferTo(resp.getOutputStream());
            } catch (FileNotFoundException ignored) {
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

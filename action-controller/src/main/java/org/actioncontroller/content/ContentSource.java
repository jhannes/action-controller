package org.actioncontroller.content;

import org.actioncontroller.util.ExceptionUtil;
import org.actioncontroller.util.IOUtil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Used to serve files from the filesystem or jar-file. In Jetty, this is a replacement for
 * DefaultServlet with the possibility of catch-everything pages, useful for SPA-applications.
 * In addition ContentSource supports serving resource-files from src/main/resources for
 * files loaded from the classpath and supports the webjar-format out of the box.
 *
 * <p>Use {@link #fromClasspath(String)}, {@link #fromURL(URL)} or {@link #fromWebJar(String)}
 * to get started</p>
 *
 */
public class ContentSource {

    private static final Properties mimeTypes = new Properties();
    static {
        try {
            mimeTypes.load(Objects.requireNonNull(ContentSource.class.getClassLoader().getResourceAsStream("mime-types.properties")));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private final URL resourceBase;
    private String fallbackPath;
    private Map<URL, Content> cache = new HashMap<>();

    protected ContentSource(URL resourceBase) {
        if (!resourceBase.getProtocol().equals("file") && !resourceBase.getProtocol().equals("jar")) {
            throw new IllegalArgumentException("Only file and jar resourceBase are supported");
        }
        if (!resourceBase.toString().endsWith("/")) {
            resourceBase = IOUtil.asURL(resourceBase + "/");
        }
        this.resourceBase = resourceBase;
    }

    public static ContentSource fromClasspath(String resourceBase) {
        // TODO: If resourceBase doesn't start with /, things go wrong
        // TODO: If resourceBase doesn't end with /, parent directory is used by accident
        URL baseResourceTmp = ContentSource.class.getResource(resourceBase);
        if (baseResourceTmp == null) {
            throw new IllegalArgumentException("Could not find resource " + resourceBase);
        }
        return fromURL(baseResourceTmp);
    }

    public static ContentSource fromURL(URL baseResource) {
        ContentSource sourceResources = replacePath(baseResource, "target/classes", "src/main/resources");
        if (sourceResources != null) {
            return sourceResources;
        }
        ContentSource testSourceResources = replacePath(baseResource, "target/test-classes", "src/test/resources");
        if (testSourceResources != null) {
            return testSourceResources;
        }
        return new ContentSource(baseResource);
    }

    private static ContentSource replacePath(URL baseResource, String oldPath, String newPath) {
        if (baseResource.toString().contains(oldPath)) {
            try {
                URL sourceResources = new URL(baseResource.toString().replaceAll(oldPath, newPath));
                sourceResources.openStream();
                return new ContentSource(sourceResources);
            } catch (FileNotFoundException ignored) {
                return null;
            } catch (IOException e) {
                throw ExceptionUtil.softenException(e);
            }
        } else {
            return null;
        }
    }

    public static ContentSource fromWebJar(String webJarName) {
        String prefix = "/META-INF/resources/webjars/" + webJarName;
        Properties properties = new Properties();
        try (InputStream pomProperties = ContentSource.class.getResourceAsStream("/META-INF/maven/org.webjars/" + webJarName + "/pom.properties")) {
            properties.load(pomProperties);
        } catch (IOException e) {
            throw ExceptionUtil.softenException(e);
        }
        return fromClasspath(prefix + "/" + properties.get("version") + "/");
    }

    public Content getContent(String relativeResource) throws IOException {
        if (relativeResource.startsWith("/")) {
            relativeResource = relativeResource.substring(1);
        }
        URL resource = new URL(resourceBase, relativeResource);

        // TODO: need to cache here

        URL resourceUrl = isDirectory(resource) ? new URL(resource, "index.html") : resource;
        if (isMissing(resourceUrl) && fallbackPath != null) {
            resourceUrl = new URL(resourceBase, fallbackPath);
        }
        // TODO: and here
        if (!isMissing(resourceUrl)) {
            return new Content(resourceUrl);
        }
        return null;
    }

    // touch
    private boolean isMissing(URL resource) throws IOException {
        try {
            resource.openStream().close();
            return false;
        } catch (FileNotFoundException e) {
            return true;
        }
    }

    private boolean isDirectory(URL resource) {
        if (resource.toString().endsWith("/")) {
            return true;
        }
        if (resource.getProtocol().equals("file")) {
            try {
                // touch
                Path path = Paths.get(resource.toURI());
                return Files.isDirectory(path);
            } catch (URISyntaxException ignored) {
            }
        }
        return false;
    }

    public ContentSource withFallbackPath(String fallbackPath) {
        this.fallbackPath = fallbackPath;
        return this;
    }
}

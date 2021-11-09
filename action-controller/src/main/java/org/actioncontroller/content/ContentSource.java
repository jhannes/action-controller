package org.actioncontroller.content;

import org.actioncontroller.util.ExceptionUtil;
import org.actioncontroller.util.IOUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;

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

    private ContentSource(URL resourceBase) {
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

    public URL resolve(String relativeResource) throws IOException {
        if (relativeResource.startsWith("/")) {
            relativeResource = relativeResource.substring(1);
        }
        URL resource = new URL(resourceBase, relativeResource);
        URL resourceUrl = isDirectory(resource) ? new URL(resource, "index.html") : resource;
        if (isMissing(resourceUrl) && fallbackPath != null) {
            resourceUrl = new URL(resourceBase, fallbackPath);
        }
        resourceUrl.openStream().close();
        return resourceUrl;
    }

    private boolean isMissing(URL resource) {
        if (resource.getProtocol().equals("file")) {
            try {
                Path path = Paths.get(resource.toURI());
                return !Files.isRegularFile(path);
            } catch (URISyntaxException ignored) {
            }
        }
        return resource.toString().endsWith("/");
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

    public Long lastModified(URL resource) {
        try {
            if (resource.getProtocol().equals("file")) {
                File file = new File(resource.toURI());
                return (file.lastModified()/1000)*1000;
            } else if (resource.getProtocol().equals("jar")) {
                JarURLConnection connection = (JarURLConnection) resource.openConnection();
                return (new File(connection.getJarFileURL().toURI()).lastModified()/1000)*1000;
            }
            return null;
        } catch (URISyntaxException | IOException e) {
            throw ExceptionUtil.softenException(e);
        }
    }

    public String getContentType(URL url) {
        String contentType = URLConnection.getFileNameMap().getContentTypeFor(url.getPath());
        if (contentType != null) {
            return contentType;
        } else {
            int lastPeriodPos = url.getPath().lastIndexOf('.');
            int lastSlashPos = url.getPath().lastIndexOf('/');
            if (lastPeriodPos > 0 && lastPeriodPos > lastSlashPos) {
                String extension = url.getPath().substring(lastPeriodPos + 1);
                return mimeTypes.getProperty(extension);
            }
        }
        return null;
    }

    public void setFallbackPath(String fallbackPath) {
        this.fallbackPath = fallbackPath;
    }
}

package org.actioncontrollerdemo;

import org.actioncontroller.util.ExceptionUtil;
import org.actioncontrollerdemo.jdkhttp.StaticContent;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
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
            mimeTypes.load(Objects.requireNonNull(StaticContent.class.getClassLoader().getResourceAsStream("mime-types.properties")));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private final URL resourceBase;

    private ContentSource(URL resourceBase) throws MalformedURLException {
        if (!resourceBase.getProtocol().equals("file") && !resourceBase.getProtocol().equals("jar")) {
            throw new IllegalArgumentException("Only file and jar resourceBase are supported");
        }
        if (!resourceBase.toString().endsWith("/")) {
            resourceBase = new URL(resourceBase + "/");
        }
        this.resourceBase = resourceBase;
    }

    public static ContentSource fromClasspath(String resourceBase) {
        // TODO: If resourceBase doesn't start with /, things go wrong
        // TODO: If resourceBase doesn't end with /, parent directory is used
        URL baseResourceTmp = ContentSource.class.getResource(resourceBase);
        if (baseResourceTmp == null) {
            throw new IllegalArgumentException("Could not find resource " + resourceBase);
        }
        return fromURL(baseResourceTmp);
    }

    public static ContentSource fromURL(URL baseResource) {
        if (baseResource.toString().contains("target/classes")) {
            try {
                URL sourceResources = new URL(baseResource.toString().replaceAll("target/classes", "src/main/resources"));
                sourceResources.openStream();
                return new ContentSource(sourceResources);
            } catch (FileNotFoundException ignored) {
            } catch (IOException e) {
                throw ExceptionUtil.softenException(e);
            }
        }
        try {
            return new ContentSource(baseResource);
        } catch (MalformedURLException e) {
            throw ExceptionUtil.softenException(e);
        }
    }

    public static ContentSource fromWebJar(String webJarName) {
        String prefix = "/META-INF/resources/webjars/" + webJarName;
        Properties properties = new Properties();
        try (InputStream pomProperties = ContentSource.class.getResourceAsStream("/META-INF/maven/org.webjars/" + webJarName + "/pom.properties")) {
            properties.load(pomProperties);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return fromClasspath(prefix + "/" + properties.get("version") + "/");
    }

    // TODO: Support fallback URL for BrowserRouter strategies
    public URL resolve(String relativeResource) throws IOException {
        if (relativeResource.startsWith("/")) {
            relativeResource = relativeResource.substring(1);
        }
        URL resource = new URL(resourceBase, relativeResource);
        URL resourceUrl = isDirectory(resource) ? new URL(resource, "index.html") : resource;
        resourceUrl.openStream().close();
        return resourceUrl;
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
}

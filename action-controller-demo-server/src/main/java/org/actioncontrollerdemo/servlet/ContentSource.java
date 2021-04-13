package org.actioncontrollerdemo.servlet;

import org.actioncontroller.ExceptionUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ContentSource {

    private final URL resourceBase;

    public ContentSource(URL resourceBase) {
        this.resourceBase = resourceBase;
        if (!resourceBase.getProtocol().equals("file") && !resourceBase.getProtocol().equals("jar")) {
            throw new IllegalArgumentException("Only file and jar resourceBase are supported");
        }
        if (!resourceBase.toString().endsWith("/")) {
            throw new IllegalArgumentException("Resource base must be a directory, was " + resourceBase);
        }
    }

    public static ContentSource fromClasspath(String resourceBase) {
        // TODO: If resourceBase doesn't start with /, things go wrong
        // TODO: If resourceBase doesn't end with /, parent directory is used
        URL baseResourceTmp = ContentSource.class.getResource(resourceBase);
        if (baseResourceTmp == null) {
            throw new IllegalArgumentException("Could not find resource " + resourceBase);
        }
        if (baseResourceTmp.toString().contains("target/classes")) {
            try {
                URL sourceResources = new URL(baseResourceTmp.toString().replaceAll("target/classes", "src/main/resources"));
                sourceResources.openStream();
                return new ContentSource(sourceResources);
            } catch (FileNotFoundException ignored) {
            } catch (IOException e) {
                throw ExceptionUtil.softenException(e);
            }
        }
        return new ContentSource(baseResourceTmp);
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

    public URL resolve(String relativeResource) throws MalformedURLException {
        URL resource = new URL(resourceBase, relativeResource);
        if (isDirectory(resource)) {
            return new URL(resource, "index.html");
        } else {
            return resource;
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
}

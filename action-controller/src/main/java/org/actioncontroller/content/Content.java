package org.actioncontroller.content;

import org.actioncontroller.util.ExceptionUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

public class Content {
    private static final Properties mimeTypes = new Properties();
    public static final DateTimeFormatter RFC_1123_DATE_TIME = DateTimeFormatter.RFC_1123_DATE_TIME;

    static {
        try {
            mimeTypes.load(Objects.requireNonNull(Content.class.getClassLoader().getResourceAsStream("mime-types.properties")));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private final URL resourceUrl;
    private final Long lastModified;
    private final Optional<String> cacheControl;

    public Content(URL resourceUrl) {
        this.resourceUrl = resourceUrl;
        try {
            if (resourceUrl.getProtocol().equals("file")) {
                File file = new File(resourceUrl.toURI());
                lastModified = (file.lastModified() / 1000) * 1000;
            } else if (resourceUrl.getProtocol().equals("jar")) {
                JarURLConnection connection = (JarURLConnection) resourceUrl.openConnection();
                lastModified = (new File(connection.getJarFileURL().toURI()).lastModified()/1000)*1000;
            } else {
                lastModified = null;
            }
        } catch (URISyntaxException | IOException e) {
            throw ExceptionUtil.softenException(e);
        }
        if (lastModified == null || lastModified > ZonedDateTime.now().minusDays(1).toInstant().toEpochMilli()) {
            cacheControl = Optional.of("no-cache");
        } else {
            cacheControl = Optional.empty();
        }
    }

    public boolean notModified(String headerInRfc1123) {
        if (headerInRfc1123 == null) {
            return false;
        }
        Instant ifModifiedSinceInstance = RFC_1123_DATE_TIME.parse(headerInRfc1123, Instant::from);
        return lastModified() <= ifModifiedSinceInstance.toEpochMilli();
    }

    public Long lastModified() {
        return lastModified;
    }

    public Optional<String> getContentType() {
        // TODO UTF-8 for html?
        String contentType = URLConnection.getFileNameMap().getContentTypeFor(resourceUrl.getPath());
        if (contentType != null) {
            return Optional.of(contentType);
        } else {
            int lastPeriodPos = resourceUrl.getPath().lastIndexOf('.');
            int lastSlashPos = resourceUrl.getPath().lastIndexOf('/');
            if (lastPeriodPos > 0 && lastPeriodPos > lastSlashPos) {
                String extension = resourceUrl.getPath().substring(lastPeriodPos + 1);
                return Optional.ofNullable(mimeTypes.getProperty(extension));
            }
            return Optional.empty();
        }
    }

    public String getLastModifiedAsRfc1123() {
        return Instant.ofEpochMilli(lastModified()).atZone(ZoneId.systemDefault()).format(RFC_1123_DATE_TIME);
    }

    public byte[] readContent() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream inputStream = resourceUrl.openStream()) {
            inputStream.transferTo(buffer);
        }
        return buffer.toByteArray();
    }

    public Optional<String> getCacheControl() {
        return cacheControl;
    }
}

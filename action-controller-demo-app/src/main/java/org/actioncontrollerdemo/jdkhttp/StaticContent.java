package org.actioncontrollerdemo.jdkhttp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.actioncontrollerdemo.ContentSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Objects;
import java.util.Properties;

public class StaticContent implements HttpHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(StaticContent.class);

    private static Properties mimeTypes = new Properties();
    static {
        try {
            mimeTypes.load(Objects.requireNonNull(StaticContent.class.getClassLoader().getResourceAsStream("mime-types.properties")));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ContentSource contentSource;

    public StaticContent(URL baseResource) {
        this(ContentSource.fromClasspath(baseResource));
    }

    public StaticContent(ContentSource contentSource) {
        this.contentSource = contentSource;
    }

    public void handle(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();
        try {
            URL resource = contentSource.resolve(uri.getPath().substring(exchange.getHttpContext().getPath().length()));
            Long lastModified = contentSource.lastModified(resource);
            String contentType = getContentType(resource);
            if (contentType != null) {
                exchange.getResponseHeaders().set("Content-type", contentType);
            }
            exchange.sendResponseHeaders(200, 0);
            InputStream inputStream = resource.openStream();
            inputStream.transferTo(exchange.getResponseBody());
        } catch (FileNotFoundException ignored) {
            exchange.sendResponseHeaders(404, 0);
        } catch (Exception e) {
            logger.error("While resolving {}", uri, e);
            exchange.sendResponseHeaders(500, 0);
            exchange.getResponseBody().write(e.toString().getBytes());
        } finally {
            exchange.close();
        }
    }

    private String getContentType(URL url) {
        String contentType =  URLConnection.getFileNameMap().getContentTypeFor(url.getPath());
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

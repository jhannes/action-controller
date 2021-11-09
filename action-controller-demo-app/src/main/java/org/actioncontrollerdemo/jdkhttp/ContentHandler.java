package org.actioncontrollerdemo.jdkhttp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.actioncontroller.content.ContentSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ContentHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(ContentHandler.class);

    public ContentSource contentSource;

    public ContentHandler(URL baseResource) {
        this(ContentSource.fromURL(baseResource));
    }

    public ContentHandler(ContentSource contentSource) {
        this.contentSource = contentSource;
    }

    public void handle(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();
        try {
            URL resource = contentSource.resolve(uri.getPath().substring(exchange.getHttpContext().getPath().length()));
            Long lastModified = contentSource.lastModified(resource);
            if (lastModified != null) {
                exchange.getResponseHeaders().set(
                        "Last-Modified",
                        DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(lastModified).atZone(ZoneId.systemDefault()))
                );

                String ifModifiedHeader = exchange.getRequestHeaders().getFirst("If-Modified-Since");
                long ifModifiedSinceHeader = ifModifiedHeader != null
                        ? DateTimeFormatter.RFC_1123_DATE_TIME.parse(ifModifiedHeader, Instant::from).toEpochMilli()
                        : -1;
                if (ifModifiedSinceHeader > 0 && lastModified >= ifModifiedSinceHeader) {
                    exchange.sendResponseHeaders(304, 0);
                    return;
                }
            }
            String contentType = contentSource.getContentType(resource);
            if (contentType != null) {
                exchange.getResponseHeaders().set("Content-type", contentType);
            }
            exchange.sendResponseHeaders(200, 0);
            try (InputStream inputStream = resource.openStream()) {
                inputStream.transferTo(exchange.getResponseBody());
            }
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

}

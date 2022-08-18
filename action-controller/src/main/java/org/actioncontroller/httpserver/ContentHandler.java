package org.actioncontroller.httpserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.actioncontroller.content.Content;
import org.actioncontroller.content.ContentSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URL;

public class ContentHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(ContentHandler.class);

    public ContentSource contentSource;

    public ContentHandler(String resourceBase) {
        this(ContentSource.fromClasspath(resourceBase));
    }

    public ContentHandler(ContentSource contentSource) {
        this.contentSource = contentSource;
    }

    public void handle(HttpExchange exchange) throws IOException {
        try {
            URI uri = exchange.getRequestURI();
            String pathInfo = uri.getPath().substring(exchange.getHttpContext().getPath().length());
            Content content = contentSource.getContent(pathInfo);
            if (content == null) {
                exchange.sendResponseHeaders(404, 0);
            } else if (content.notModified(exchange.getRequestHeaders().getFirst("If-Modified-Since"))) {
                exchange.sendResponseHeaders(304, -1);
            } else {
                content.getContentType().ifPresent(contentType -> exchange.getResponseHeaders().set("Content-type", contentType));
                content.getCacheControl().ifPresent(header -> exchange.getResponseHeaders().set("Cache-Control", header));
                exchange.getResponseHeaders().set("Last-Modified", content.getLastModifiedAsRfc1123());
                byte[] data = content.readContent();
                exchange.sendResponseHeaders(200, data.length);
                exchange.getResponseBody().write(data);
            }
        } catch (Exception e) {
            logger.error("While responding to {}", exchange.getRequestURI(), e);
            exchange.sendResponseHeaders(500, 0);
        } finally {
            exchange.close();
        }
    }

}

package org.actioncontroller.exceptions;

import org.actioncontroller.ApiHttpExchange;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class HttpNotModifiedException extends HttpActionException {
    private final ZonedDateTime lastModified;

    public HttpNotModifiedException(ZonedDateTime lastModified) {
        super(304, "Not modified");
        this.lastModified = lastModified;
    }

    @Override
    public void sendError(ApiHttpExchange exchange) throws IOException {
        exchange.setStatus(getStatusCode());
        if (lastModified != null) {
            exchange.setResponseHeader("Last-modified", lastModified.format(DateTimeFormatter.RFC_1123_DATE_TIME));
        }
    }
}

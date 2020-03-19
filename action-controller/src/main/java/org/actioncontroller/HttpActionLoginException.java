package org.actioncontroller;

import org.actioncontroller.meta.ApiHttpExchange;

import java.io.IOException;

public class HttpActionLoginException extends HttpActionException {
    public HttpActionLoginException(String message) {
        super(401, message);
    }

    @Override
    public void sendError(ApiHttpExchange exchange) throws IOException {
        exchange.authenticate();
    }
}

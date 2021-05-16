package org.actioncontroller.exceptions;

import org.actioncontroller.ApiHttpExchange;

import java.io.IOException;

/**
 * Indicates an unexpected event on the server during the invocation of the action.
 */
public class HttpServerErrorException extends HttpActionException {
    public HttpServerErrorException(Throwable e) {
        super(500, e);
    }

    public HttpServerErrorException(String message) {
        super(500, message);
    }

    @Override
    public void sendError(ApiHttpExchange exchange) throws IOException {
        exchange.sendError(getStatusCode(), "Internal Server Error");
    }
}

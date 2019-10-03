package org.actioncontroller;

import org.actioncontroller.meta.ApiHttpExchange;

import java.io.IOException;

/**
 * Indicates an unexpected event on the server during the invocation of the action.
 */
public class HttpServerErrorException extends HttpActionException {
    public HttpServerErrorException(Throwable e) {
        super(500, e);
    }

    @Override
    public void sendError(ApiHttpExchange exchange) throws IOException {
        exchange.sendError(getStatusCode(), "Internal Server Error");
    }
}

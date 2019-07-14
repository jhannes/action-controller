package org.actioncontroller;

import org.actioncontroller.meta.ApiHttpExchange;

import java.io.IOException;

public class HttpServerErrorException extends HttpActionException {
    public HttpServerErrorException(Throwable e) {
        super(500, e);
    }

    @Override
    public void sendError(ApiHttpExchange exchange) throws IOException {
        exchange.sendError(getStatusCode());
    }
}

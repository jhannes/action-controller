package org.actioncontroller;

import org.actioncontroller.meta.ApiHttpExchange;

import java.io.IOException;

public class HttpActionException extends RuntimeException {

    private int statusCode;

    public HttpActionException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public HttpActionException(int statusCode, Throwable e) {
        super(e);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    @Override
    public String toString() {
        return getClass().getName() + ": " + getStatusCode() + " " + getMessage();
    }

    public void sendError(ApiHttpExchange exchange) throws IOException {
        exchange.sendError(getStatusCode(), getMessage());
    }
}

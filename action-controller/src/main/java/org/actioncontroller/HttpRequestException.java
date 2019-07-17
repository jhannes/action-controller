package org.actioncontroller;

public class HttpRequestException extends HttpActionException {
    public HttpRequestException(RuntimeException e) {
        super(400, e);
    }

    public HttpRequestException(String message) {
        super(400, message);
    }
}

package org.actioncontroller;

public class HttpRequestException extends HttpActionException {
    public HttpRequestException(String message) {
        super(400, message);
    }
}

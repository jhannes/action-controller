package org.actioncontroller.exceptions;

public class HttpForbiddenException extends HttpRequestException {

    public HttpForbiddenException(String message) {
        super(403, message);
    }

    public HttpForbiddenException() {
        this("Forbidden");
    }
}

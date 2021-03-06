package org.actioncontroller.exceptions;

public class HttpNotFoundException extends HttpRequestException {

    public HttpNotFoundException(String message) {
        super(404, message);
    }

    public HttpNotFoundException() {
        this("Not Found");
    }
}

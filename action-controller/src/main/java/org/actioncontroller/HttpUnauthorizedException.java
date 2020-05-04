package org.actioncontroller;

public class HttpUnauthorizedException extends HttpRequestException {

    public HttpUnauthorizedException(String message) {
        super(401, message);
    }

    public HttpUnauthorizedException() {
        this("Unauthorized");
    }
}

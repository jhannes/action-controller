package org.actioncontroller;

/**
 * Indicate that an action could not be executed due to missing or incorrect
 * HTTP request values.
 */
public class HttpRequestException extends HttpActionException {
    public HttpRequestException(Exception e) {
        super(400, e);
    }

    public HttpRequestException(String message) {
        this(400, message);
    }

    public HttpRequestException(int code, String message) {
        super(code, message);
    }
}

package org.actioncontroller;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class HttpRequestException extends RuntimeException {

    private int statusCode;

    public HttpRequestException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public HttpRequestException(int statusCode, Throwable e) {
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

    public void sendError(HttpServletResponse resp) throws IOException {
        resp.sendError(getStatusCode(), getMessage());
    }

}

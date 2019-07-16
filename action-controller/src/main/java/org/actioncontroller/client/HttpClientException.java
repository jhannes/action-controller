package org.actioncontroller.client;

import org.actioncontroller.HttpActionException;

import java.net.URL;
import java.util.Objects;

public class HttpClientException extends HttpActionException {
    private final URL url;

    public HttpClientException(int responseCode, String responseMessage, URL url) {
        super(responseCode, responseMessage);
        this.url = url;
    }

    @Override
    public String toString() {
        return getClass().getName() + ": " + getStatusCode() + " " + getMessage() + " [" + url + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HttpClientException that = (HttpClientException) o;
        return Objects.equals(getStatusCode(), that.getStatusCode()) &&
                Objects.equals(getMessage(), that.getMessage());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getStatusCode(), getMessage());
    }
}

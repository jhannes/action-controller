package org.actioncontroller.client;

import org.actioncontroller.HttpActionException;

import java.net.URL;
import java.util.Objects;

public class HttpClientException extends HttpActionException {
    private final String responseBody;
    private final URL url;

    public HttpClientException(int responseCode, String responseMessage, String responseBody, URL url) {
        super(responseCode, responseMessage);
        this.responseBody = responseBody;
        this.url = url;
    }

    public HttpClientException(int responseCode, String responseMessage) {
        super(responseCode, responseMessage);
        this.responseBody = null;
        this.url = null;
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
                (Objects.equals(getMessage(), that.getMessage()) ||
                        Objects.equals(getMessage(), that.responseBody) ||
                        Objects.equals(responseBody, that.getMessage()));
    }

    @Override
    public int hashCode() {
        return Objects.hash(getStatusCode());
    }
}

package org.actioncontroller.client;

import java.net.URL;
import java.util.Objects;

/**
 * Thrown by {@link ApiClientProxy} when the HTTP request returned a failure status code.
 */
public class HttpClientException extends RuntimeException {
    private final String responseBody;
    private final URL url;
    private int statusCode;

    public HttpClientException(int responseCode, String responseMessage, String responseBody, URL url) {
        super(responseMessage);
        this.statusCode = responseCode;
        this.responseBody = responseBody;
        this.url = url;
    }

    public HttpClientException(int responseCode, String responseMessage) {
        this(responseCode, responseMessage, null, null);
    }

    public int getStatusCode() {
        return statusCode;
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

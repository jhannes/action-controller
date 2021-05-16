package org.actioncontroller.exceptions;

import org.actioncontroller.ApiHttpExchange;

import java.io.IOException;

public class HttpRedirectException extends HttpActionException {
    private String url;

    public HttpRedirectException(ApiHttpExchange exchange, String pathInfo) {
        this(exchange.getApiURL() + "/" + pathInfo);
    }

    public HttpRedirectException(String url) {
        super(302, "Redirect to " + url);
        this.url = url;
    }

    @Override
    public void sendError(ApiHttpExchange exchange) throws IOException {
        exchange.sendRedirect(url);
    }
}

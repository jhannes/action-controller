package org.actioncontrollerdemo.jdkhttp;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

public interface HttpExchangeHandler {
    boolean handle(HttpExchange exchange) throws IOException;
}

package org.actioncontroller.httpserver;

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.HttpExchange;
import org.actioncontroller.meta.ApiHttpExchange;

import java.io.IOException;

/**
 * Used with {@link com.sun.net.httpserver.HttpServer} as a base class for authentication with action-controller
 */
public abstract class ActionAuthenticator extends Authenticator {
    public abstract Result authenticate(HttpExchange exchange);

    public abstract void login(ApiHttpExchange exchange) throws IOException;
}

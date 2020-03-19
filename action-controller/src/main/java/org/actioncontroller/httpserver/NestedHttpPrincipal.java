package org.actioncontroller.httpserver;

import com.sun.net.httpserver.HttpPrincipal;

import java.security.Principal;

/**
 * Used with {@link com.sun.net.httpserver.HttpServer} to provide a principal that doesn't directly
 * extend {@link HttpPrincipal}
 */
public class NestedHttpPrincipal extends HttpPrincipal {
    private final Principal principal;

    public NestedHttpPrincipal(String realm, Principal principal) {
        super(principal.getName(), realm);
        this.principal = principal;
    }

    public Principal getPrincipal() {
        return principal;
    }
}

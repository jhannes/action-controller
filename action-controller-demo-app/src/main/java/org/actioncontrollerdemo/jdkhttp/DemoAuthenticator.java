package org.actioncontrollerdemo.jdkhttp;

import com.sun.net.httpserver.HttpExchange;
import org.actioncontroller.ActionControllerCookie;
import org.actioncontroller.httpserver.ActionAuthenticator;
import org.actioncontroller.httpserver.NestedHttpPrincipal;
import org.actioncontroller.ApiHttpExchange;
import org.actioncontrollerdemo.DemoPrincipal;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class DemoAuthenticator extends ActionAuthenticator {

    @Override
    public Result authenticate(HttpExchange exchange) {
        return ActionControllerCookie.parseClientCookies(exchange.getRequestHeaders().get("Cookie"))
                .getOrDefault("username", List.of())
                .stream()
                .findFirst()
                .map(username -> new Success(new NestedHttpPrincipal("demo", DemoPrincipal.createPrincipal(username))))
                .orElseGet(() -> new Success(null));
    }

    @Override
    public void login(ApiHttpExchange exchange) throws IOException {
        exchange.sendRedirect("/demo/api/login?redirectAfterLogin="
                + URLEncoder.encode(exchange.getRequestURL(), StandardCharsets.UTF_8));
    }
}


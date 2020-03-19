package org.actioncontrollerdemo.jdkhttp;

import com.sun.net.httpserver.HttpExchange;
import org.actioncontroller.httpserver.ActionAuthenticator;
import org.actioncontroller.httpserver.NestedHttpPrincipal;
import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontrollerdemo.AdminPrincipal;
import org.actioncontrollerdemo.DemoPrincipal;
import org.actioncontrollerdemo.DemoUser;

import java.io.IOException;
import java.net.HttpCookie;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class DemoAuthenticator extends ActionAuthenticator {

    private DemoUser fetchUserInfo(String username) {
        return new DemoUser(username);
    }

    @Override
    public Result authenticate(HttpExchange exchange) {
        String username = getCookie(exchange, "username");
        if (username == null) {
            return new Success(null);
        }
        DemoUser user = fetchUserInfo(username);
        return user.getUsername().equals("johannes")
                ? new Success(new NestedHttpPrincipal("demo", new AdminPrincipal(user)))
                : new Success(new NestedHttpPrincipal("demo", new DemoPrincipal(user)));
    }

    private String getCookie(HttpExchange exchange, String cookieName) {
        if (!exchange.getRequestHeaders().containsKey("Cookie")) {
            return null;
        }
        return HttpCookie.parse(exchange.getRequestHeaders().getFirst("Cookie"))
                .stream()
                .filter(c -> c.getName().equals(cookieName))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElse(null);
    }

    @Override
    public void login(ApiHttpExchange exchange) throws IOException {
        exchange.sendRedirect("/demo/api/login?redirectAfterLogin="
                + URLEncoder.encode(exchange.getRequestURL(), StandardCharsets.UTF_8));
    }
}


package org.actioncontroller;

import com.sun.net.httpserver.HttpExchange;
import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontrollerdemo.jdkhttp.HttpExchangeHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ApiHandler implements HttpExchangeHandler, UserContext {
    private Map<String, List<ApiControllerAction>> routes;
    private String context;
    private String apiPath;

    public ApiHandler(String context, String apiPath, Object controller) {
        this.context = context;
        this.apiPath = apiPath;
        routes = ApiControllerAction.registerActions(controller);
    }

    @Override
    public boolean handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!path.startsWith(context+apiPath)) {
            return false;
        }
        JdkHttpExchange httpExchange = new JdkHttpExchange(exchange, context, apiPath);
        String pathInfo = path.substring((context+apiPath).length());
        for (ApiControllerAction action : routes.get(exchange.getRequestMethod())) {
            if (action.matches(pathInfo)) {
                httpExchange.setPathParameters(action.collectPathParameters(pathInfo));
                invoke(httpExchange, action);
                return true;
            }
        }
        return false;
    }

    private void invoke(ApiHttpExchange exchange, ApiControllerAction apiRoute) throws IOException {
        try {
            apiRoute.invoke(this, exchange);
        } catch (HttpActionException e) {
            e.sendError(exchange);
        }
    }

    @Override
    public boolean isUserLoggedIn(ApiHttpExchange exchange) {
        return exchange.isUserLoggedIn();
    }

    @Override
    public boolean isUserInRole(ApiHttpExchange exchange, String role) {
        return exchange.isUserInRole(role);
    }
}

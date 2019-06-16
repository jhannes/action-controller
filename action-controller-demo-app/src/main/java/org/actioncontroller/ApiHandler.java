package org.actioncontroller;

import com.sun.net.httpserver.HttpExchange;
import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontrollerdemo.jdkhttp.HttpExchangeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiHandler implements HttpExchangeHandler, UserContext {
    private static final Logger logger = LoggerFactory.getLogger(ApiHandler.class);

    private Map<String, List<ApiServletAction>> routes = new HashMap<>();
    {
        routes.put("GET", new ArrayList<>());
        routes.put("POST", new ArrayList<>());
        routes.put("PUT", new ArrayList<>());
        routes.put("DELETE", new ArrayList<>());
    }
    private String context;
    private String apiPath;

    public ApiHandler(String context, String apiPath, Object controller) {
        this.context = context;
        this.apiPath = apiPath;
        ApiServletAction.registerActions(controller, routes);
    }

    @Override
    public boolean handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!path.startsWith(context+apiPath)) {
            return false;
        }
        String pathInfo = path.substring((context+apiPath).length());
        for (ApiServletAction action : routes.get(exchange.getRequestMethod())) {
            if (action.matches(pathInfo)) {
                JdkHttpExchange httpExchange = new JdkHttpExchange(exchange, action.collectPathParameters(pathInfo), context, apiPath);
                invoke(httpExchange, action);
                return true;
            }
        }
        return false;
    }

    private void invoke(ApiHttpExchange exchange, ApiServletAction apiRoute) throws IOException {
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

package org.actioncontroller.httpserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.actioncontroller.ApiControllerAction;
import org.actioncontroller.HttpActionException;
import org.actioncontroller.UserContext;
import org.actioncontroller.meta.ApiHttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ApiHandler implements UserContext, HttpHandler {
    private static Logger logger = LoggerFactory.getLogger(ApiHandler.class);

    private Map<String, List<ApiControllerAction>> routes;
    private String context;
    private String apiPath;

    public ApiHandler(String context, String apiPath, Object controller) {
        this.context = context;
        this.apiPath = apiPath;
        routes = ApiControllerAction.registerActions(controller);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        JdkHttpExchange httpExchange = new JdkHttpExchange(exchange, context, apiPath);
        String controllerPath = context + apiPath;
        String pathInfo = path.substring(controllerPath.length());
        String method = exchange.getRequestMethod();
        for (ApiControllerAction action : routes.get(method)) {
            if (action.matches(pathInfo)) {
                httpExchange.setPathParameters(action.collectPathParameters(pathInfo));
                invoke(httpExchange, action);
                httpExchange.close();
                return;
            }
        }
        logger.warn("No route for {} {}[{}]", method, controllerPath, pathInfo);
        httpExchange.sendError(404, "No route for " + method + ": " + controllerPath + pathInfo);
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

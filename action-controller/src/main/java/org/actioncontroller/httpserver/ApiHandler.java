package org.actioncontroller.httpserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.actioncontroller.ApiControllerAction;
import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.ApiControllerMethodAction;
import org.actioncontroller.HttpActionException;
import org.actioncontroller.UserContext;
import org.actioncontroller.meta.ApiHttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class ApiHandler implements UserContext, HttpHandler {
    private static Logger logger = LoggerFactory.getLogger(ApiHandler.class);

    private List<ApiControllerAction> actions;

    public ApiHandler(Object controller, ApiControllerContext apiContext) {
        actions = ApiControllerMethodAction.registerActions(controller, apiContext);
    }

    public ApiHandler(Object controller) {
        this(controller, new ApiControllerContext());
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        JdkHttpExchange httpExchange = new JdkHttpExchange(exchange);
        for (ApiControllerAction action : actions) {
            if (action.matches(httpExchange)) {
                try {
                    action.invoke(this, httpExchange);
                } catch (HttpActionException e) {
                    e.sendError(httpExchange);
                } catch (Exception e) {
                    logger.error("While handling {} with {}", httpExchange, action, e);
                    httpExchange.sendError(500, "Internal server error");
                }
                httpExchange.close();
                return;
            }
        }
        logger.warn("No route for {}", httpExchange);
        logger.info("Routes {}", actions);
        httpExchange.sendError(404, "No route for " + httpExchange.getHttpMethod() + ": " + httpExchange.getApiURL() + httpExchange.getPathInfo());
        httpExchange.close();
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

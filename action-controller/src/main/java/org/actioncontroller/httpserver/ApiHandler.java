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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ApiHandler implements UserContext, HttpHandler {
    private static Logger logger = LoggerFactory.getLogger(ApiHandler.class);

    private List<ApiControllerAction> actions = new ArrayList<>();

    public ApiHandler(Object[] controllers, ApiControllerContext apiContext) {
        for (Object controller : controllers) {
            actions.addAll(ApiControllerMethodAction.registerActions(controller, apiContext));
        }
    }

    public ApiHandler(Object controller, ApiControllerContext apiContext) {
        this(new Object[] { controller }, apiContext);
    }

    public ApiHandler(Object[] controllers) {
        this(controllers, new ApiControllerContext());
    }

    public ApiHandler(Object controller) {
        this(controller, new ApiControllerContext());
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        JdkHttpExchange httpExchange = new JdkHttpExchange(exchange);
        List<ApiControllerAction> candidates = new ArrayList<>();

        for (ApiControllerAction action : actions) {
            if (action.matches(httpExchange)) {
                candidates.add(action);
            }
        }

        if (candidates.size() > 1) {
            candidates = candidates.stream().filter(ApiControllerAction::requiresParameter).collect(Collectors.toList());
        }

        if (candidates.size() == 1) {
            try {
                candidates.get(0).invoke(this, httpExchange);
            } catch (HttpActionException e) {
                e.sendError(httpExchange);
            } catch (Exception e) {
                logger.error("While handling {} with {}", httpExchange, candidates.get(0), e);
                httpExchange.sendError(500, "Internal server error");
            }
            httpExchange.close();
        } else if (candidates.isEmpty()) {
            logger.info("No route for {}", httpExchange.getHttpMethod() + " " + httpExchange.getApiURL().getPath() + "[" + httpExchange.getPathInfo() + "]");
            logger.debug("Routes {}", actions);
            httpExchange.sendError(404, "No route for " + httpExchange.getHttpMethod() + ": " + httpExchange.getApiURL() + httpExchange.getPathInfo());
            httpExchange.close();
        } else {
            logger.warn("Ambiguous route for {}", httpExchange.getHttpMethod() + " " + httpExchange.getApiURL().getPath() + "[" + httpExchange.getPathInfo() + "]");
            logger.debug("Routes {}", candidates);
            httpExchange.sendError(404, "No route for " + httpExchange.getHttpMethod() + ": " + httpExchange.getApiURL() + httpExchange.getPathInfo());
            httpExchange.close();
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

package org.actioncontroller.httpserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.TimerRegistry;
import org.actioncontroller.UserContext;
import org.actioncontroller.ApiHttpExchange;
import org.actioncontroller.exceptions.ActionControllerConfigurationCompositeException;
import org.actioncontroller.ApiControllerActionRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;

public class ApiHandler implements UserContext, HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiHandler.class);

    private final ApiControllerActionRouter router = new ApiControllerActionRouter();
    private TimerRegistry timerRegistry = TimerRegistry.NULL;

    public ApiHandler(Object[] controllers, ApiControllerContext apiContext) {
        ActionControllerConfigurationCompositeException controllerException = new ActionControllerConfigurationCompositeException();
        router.setupActions(Arrays.asList(controllers), apiContext, controllerException);
        controllerException.verifyNoExceptions();
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
        try (JdkHttpExchange httpExchange = new JdkHttpExchange(exchange)) {
            try {
                router.invokeAction(httpExchange, this);
            } catch (Exception e) {
                logger.error("While handling {}", httpExchange, e);
                httpExchange.sendError(500, "Internal server error");
            }
        }
    }

    @Override
    public TimerRegistry getTimerRegistry() {
        return timerRegistry;
    }

    public void setTimerRegistry(TimerRegistry timerRegistry) {
        this.timerRegistry = timerRegistry;
    }
}

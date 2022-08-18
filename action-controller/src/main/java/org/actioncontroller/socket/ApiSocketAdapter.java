package org.actioncontroller.socket;

import org.actioncontroller.ApiControllerActionRouter;
import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.TimerRegistry;
import org.actioncontroller.UserContext;
import org.actioncontroller.exceptions.ActionControllerConfigurationCompositeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;

public class ApiSocketAdapter implements UserContext {
    private static final Logger logger = LoggerFactory.getLogger(ApiSocketAdapter.class);

    private final ApiControllerActionRouter router = new ApiControllerActionRouter();
    private final TimerRegistry timerRegistry = TimerRegistry.NULL;

    public ApiSocketAdapter(Object controller) {
        this(controller, new ApiControllerContext());
    }

    public ApiSocketAdapter(Object controller, ApiControllerContext context) {
        this(new Object[] { controller }, context);
    }

    public ApiSocketAdapter(Object[] controllers, ApiControllerContext apiContext) {
        ActionControllerConfigurationCompositeException controllerException = new ActionControllerConfigurationCompositeException();
        router.setupActions(Arrays.asList(controllers), apiContext, controllerException);
        controllerException.verifyNoExceptions();
    }

    public void handle(Socket socket) throws IOException {
        try (SocketHttpExchange exchange = new SocketHttpExchange(socket)) {
            try {
                router.invokeAction(exchange, this);
            } catch (Exception e) {
                logger.error("While handling {}", exchange, e);
                exchange.sendError(500, "Internal server error");
            }
        }
    }

    @Override
    public TimerRegistry getTimerRegistry() {
        return timerRegistry;
    }
}

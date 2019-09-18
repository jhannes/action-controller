package org.actioncontroller;

import org.actioncontroller.meta.ApiHttpExchange;

import java.io.IOException;

public interface ApiControllerAction {
    boolean matches(ApiHttpExchange exchange);

    void invoke(UserContext userContext, ApiHttpExchange exchange) throws IOException;

    boolean requiresParameter();
}

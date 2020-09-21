package org.actioncontroller;

import org.actioncontroller.meta.ApiHttpExchange;

public interface UserContext {
    boolean isUserLoggedIn(ApiHttpExchange exchange);

    boolean isUserInRole(ApiHttpExchange exchange, String role);

    TimerRegistry getTimerRegistry();
}

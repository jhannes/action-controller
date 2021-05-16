package org.actioncontroller;

public interface UserContext {
    boolean isUserLoggedIn(ApiHttpExchange exchange);

    boolean isUserInRole(ApiHttpExchange exchange, String role);

    TimerRegistry getTimerRegistry();
}

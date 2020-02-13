package org.actioncontroller;

import org.actioncontroller.meta.ApiHttpExchange;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * The core of the framework. {@link org.actioncontroller.servlet.ApiServlet} and
 * {@link org.actioncontroller.httpserver.ApiHandler} finds an action to perform
 * based on {@link #matches} and {@link #invoke} this action.
 */
public interface ApiControllerAction {
    /**
     * Returns whether this action is appropriate to handle the given {@link ApiHttpExchange}
     */
    boolean matches(ApiHttpExchange exchange);

    /**
     * Returns whether this action is appropriate to handle the given path
     */
    boolean matches(String pathInfo);

    /**
     * Invokes this action on the given {@link ApiHttpExchange} by converting the
     * exchange to method invocation arguments, invoking the method and converting
     * the return value.
     */
    void invoke(UserContext userContext, ApiHttpExchange exchange) throws IOException;

    /**
     * Returns the controller that this action belongs to
     */
    Object getController();

    /**
     * Returns the method that implements this action
     */
    Method getAction();

    /**
     * Returns the request URL pattern used to match this action
     */
    String getPattern();

    /**
     * Returns the request HTTP Method used to match this action
     */
    String getHttpMethod();

    /**
     * Returns true if the action requires a request parameter, such as
     * <code>&#064;Get("/test?error")</code>. When more than one action
     * returns true from {@link #matches} and exactly one action
     * returns true from requiresParameter, that action is invokes.
     * Otherwise action-controller throws an exception
     */
    boolean requiresParameter();
}

package org.actioncontroller.meta;

import org.actioncontroller.ApiHttpExchange;

import java.io.IOException;

/**
 * Returned by {@link HttpParameterMapperFactory} to be used with
 * {@link ApiHttpExchange} to convert HTTP request information
 * into method invocation arguments.
 */
@FunctionalInterface
public interface HttpParameterMapper {

    Object apply(ApiHttpExchange exchange) throws Exception;

    /**
     * Invoked after the action method was completed successfully
     */
    default void onComplete(ApiHttpExchange exchange, Object argument){
    }
}

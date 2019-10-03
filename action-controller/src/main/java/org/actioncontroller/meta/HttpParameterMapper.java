package org.actioncontroller.meta;

import java.io.IOException;

/**
 * Returned by {@link HttpParameterMapperFactory} to be used with
 * {@link ApiHttpExchange} to convert HTTP request information
 * into method invocation arguments.
 */
@FunctionalInterface
public interface HttpParameterMapper {

    Object apply(ApiHttpExchange exchange) throws IOException;

}

package org.actioncontroller.meta;

import java.io.IOException;

/**
 * Returned by {@link HttpReturnMapperFactory} to be used with
 * {@link ApiHttpExchange} to method invocation return values to
 * HTTP response information.
 */
@FunctionalInterface
public interface HttpReturnMapper {

    void accept(Object result, ApiHttpExchange exchange) throws IOException;


}

package org.actioncontroller.meta;

import org.actioncontroller.client.ApiClientExchange;

import java.io.IOException;

/**
 * Returned by {@link HttpReturnMapperFactory} to be used with
 * {@link org.actioncontroller.client.ApiClientProxy} to convert
 * HTTP response information into method return values.
 */
@FunctionalInterface
public interface HttpClientReturnMapper {
    Object getReturnValue(ApiClientExchange exchange) throws IOException;
}

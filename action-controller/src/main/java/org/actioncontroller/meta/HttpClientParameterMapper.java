package org.actioncontroller.meta;

import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.client.ApiClientExchange;

import java.io.IOException;

/**
 * Returned by {@link HttpParameterMapperFactory} to be used with
 * {@link ApiClientClassProxy} to convert
 * method arguments into HTTP request information.
 */
@FunctionalInterface
public interface HttpClientParameterMapper {
    void apply(ApiClientExchange exchange, Object arg) throws IOException;
}

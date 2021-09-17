package org.actioncontroller.meta;

import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.client.ApiClientExchange;

import java.io.IOException;

/**
 * Returned by {@link HttpReturnMapperFactory} to be used with
 * {@link ApiClientClassProxy} to convert
 * HTTP response information into method return values.
 */
@FunctionalInterface
public interface HttpClientReturnMapper {
    Object getReturnValue(ApiClientExchange exchange) throws IOException;

    default void setupExchange(ApiClientExchange exchange) {}

    static HttpClientReturnMapper withHeader(HttpClientReturnMapper mapper, String headerName, String headerValue) {
        return new HttpClientReturnMapper() {
            @Override
            public Object getReturnValue(ApiClientExchange exchange) throws IOException {
                return mapper.getReturnValue(exchange);
            }

            @Override
            public void setupExchange(ApiClientExchange exchange) {
                exchange.setHeader(headerName, headerValue);
            }
        };
    }

}

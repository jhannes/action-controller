package org.actioncontroller.meta;

import org.actioncontroller.client.ApiClientExchange;

import java.io.IOException;

@FunctionalInterface
public interface HttpClientReturnMapper {
    Object getReturnValue(ApiClientExchange exchange) throws IOException;
}

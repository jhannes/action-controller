package org.actioncontroller.meta;

import org.actioncontroller.client.ApiClientExchange;

@FunctionalInterface
public interface HttpClientParameterMapper {
    void apply(ApiClientExchange exchange, Object arg);
}

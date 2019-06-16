package org.actioncontroller.meta;

import java.io.IOException;

@FunctionalInterface
public interface HttpRequestParameterMapping {

    Object apply(ApiHttpExchange exchange) throws IOException;

}
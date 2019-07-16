package org.actioncontroller.meta;

import java.io.IOException;

@FunctionalInterface
public interface HttpParameterMapper {

    Object apply(ApiHttpExchange exchange) throws IOException;

}
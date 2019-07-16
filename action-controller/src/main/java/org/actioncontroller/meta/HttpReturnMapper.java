package org.actioncontroller.meta;

import java.io.IOException;

@FunctionalInterface
public interface HttpReturnMapper {

    void accept(Object result, ApiHttpExchange exchange) throws IOException;


}

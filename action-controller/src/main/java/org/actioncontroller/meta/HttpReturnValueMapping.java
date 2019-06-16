package org.actioncontroller.meta;

import java.io.IOException;

@FunctionalInterface
public interface HttpReturnValueMapping {

    void accept(Object result, ApiHttpExchange exchange) throws IOException;


}

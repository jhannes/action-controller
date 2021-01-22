package org.actioncontroller.json;

import org.actioncontroller.HttpActionException;
import org.actioncontroller.meta.ApiHttpExchange;
import org.jsonbuddy.JsonNode;
import org.jsonbuddy.JsonObject;

import java.io.IOException;

/**
 * Used to return a JsonObject to the user agent along with an error status code
 */
public class JsonHttpActionException extends HttpActionException {

    private final JsonNode jsonObject;

    public JsonHttpActionException(int errorCode, String message, JsonNode json) {
        super(errorCode, message);
        this.jsonObject = json;
    }

    @Override
    public void sendError(ApiHttpExchange exchange) throws IOException {
        exchange.setStatus(getStatusCode());
        exchange.write("application/json", jsonObject::toJson);
    }

}

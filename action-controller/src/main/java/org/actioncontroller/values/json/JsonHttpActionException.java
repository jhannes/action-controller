package org.actioncontroller.values.json;

import org.actioncontroller.exceptions.HttpActionException;
import org.actioncontroller.ApiHttpExchange;
import org.jsonbuddy.JsonNode;

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

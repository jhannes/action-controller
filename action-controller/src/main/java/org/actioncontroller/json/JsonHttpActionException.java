package org.actioncontroller.json;

import org.actioncontroller.HttpActionException;
import org.actioncontroller.meta.ApiHttpExchange;
import org.jsonbuddy.JsonObject;

import java.io.IOException;

/**
 * Used to return a JsonObject to the user agent along with an error status code
 */
public class JsonHttpActionException extends HttpActionException {

    private JsonObject jsonObject;

    public JsonHttpActionException(int errorCode, String message, JsonObject jsonObject) {
        super(errorCode, message);
        this.jsonObject = jsonObject;
    }

    @Override
    public void sendError(ApiHttpExchange exchange) throws IOException {
        exchange.sendError(getStatusCode(), getMessage());
        exchange.write("application/json", writer -> jsonObject.toJson(writer));
    }

}

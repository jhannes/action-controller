package org.actioncontroller.json;

import org.actioncontroller.HttpRequestException;
import org.jsonbuddy.JsonObject;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class JsonHttpRequestException extends HttpRequestException {

    private JsonObject jsonObject;

    public JsonHttpRequestException(int errorCode, String message, JsonObject jsonObject) {
        super(errorCode, message);
        this.jsonObject = jsonObject;
    }

    @Override
    public void sendError(HttpServletResponse resp) throws IOException {
        resp.setStatus(getStatusCode());
        resp.setContentType("application/json");
        jsonObject.toJson(resp.getWriter());
    }

}

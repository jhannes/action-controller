package org.actioncontrollerdemo;

import org.actioncontroller.values.ContentBody;
import org.actioncontroller.actions.GET;
import org.actioncontroller.values.RequestParam;
import org.actioncontroller.values.json.JsonBody;
import org.jsonbuddy.JsonObject;

import java.util.Optional;

public class TestController {
    private Runnable updater;

    public TestController() {
        this.updater = () -> {};
    }

    public TestController(Runnable updater) {
        this.updater = updater;
    }

    @GET("/test")
    @ContentBody
    public String sayHello(
            @RequestParam("name") Optional<String> name
    ) {
        return "Hello " + name.orElse("world");
    }

    @GET("/update")
    public void update() {
        updater.run();
    }

    @JsonBody
    @GET("/json")
    public JsonObject getJson() {
        return new JsonObject().put("product", "Blåbærsyltetøy");
    }
}

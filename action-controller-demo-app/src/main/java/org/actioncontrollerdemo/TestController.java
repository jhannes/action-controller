package org.actioncontrollerdemo;

import org.actioncontroller.ContentBody;
import org.actioncontroller.GET;
import org.actioncontroller.RequestParam;
import org.actioncontroller.json.JsonBody;
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

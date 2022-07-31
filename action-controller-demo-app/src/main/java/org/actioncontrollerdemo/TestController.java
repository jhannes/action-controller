package org.actioncontrollerdemo;

import org.actioncontroller.actions.GET;
import org.actioncontroller.values.ContentBody;
import org.actioncontroller.values.RequestParam;
import org.actioncontroller.values.UnencryptedCookiePreview;
import org.actioncontroller.values.json.JsonBody;
import org.jsonbuddy.JsonObject;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class TestController {
    private final Runnable updater;

    public TestController() {
        this.updater = () -> {};
    }

    public TestController(Runnable updater) {
        this.updater = updater;
    }

    @GET("/test")
    @ContentBody
    public String sayHello(
            @RequestParam("name") Optional<String> name,
            @UnencryptedCookiePreview("value") AtomicReference<String> cookieValue
    ) {
        String user = name
                .or(() -> Optional.ofNullable(cookieValue.get()))
                .orElse("<no username>");

        name.ifPresentOrElse(cookieValue::set, () -> cookieValue.set(null));
        return "Hello " + user;
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

package org.actioncontrollerdemo;

import org.actioncontroller.ContentBody;
import org.actioncontroller.Get;
import org.actioncontroller.RequestParam;

import java.util.Optional;

public class TestController {
    private Runnable updater;

    public TestController(Runnable updater) {
        this.updater = updater;
    }

    @Get("/test")
    @ContentBody
    public String sayHello(
            @RequestParam("name") Optional<String> name
    ) {
        return "Hello " + name.orElse("world");
    }

    @Get("/update")
    public void update() {
        updater.run();
    }
}

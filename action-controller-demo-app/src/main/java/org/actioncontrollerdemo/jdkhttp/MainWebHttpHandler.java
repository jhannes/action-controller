package org.actioncontrollerdemo.jdkhttp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.actioncontroller.ApiHandler;
import org.actioncontrollerdemo.TestController;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

public class MainWebHttpHandler implements HttpHandler {

    private List<HttpExchangeHandler> handlerList = new ArrayList<>();

    public MainWebHttpHandler() throws MalformedURLException {
        handlerList.add(StaticContent.createWebJar("swagger-ui", "/demo/swagger"));
        handlerList.add(new StaticContent(getClass().getResource("/webapp-actioncontrollerdemo"), "/demo"));

        handlerList.add(new ApiHandler("/demo", "/api", new TestController(() -> {
            System.out.println("Hello");
        })));
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        for (HttpExchangeHandler staticContent : handlerList) {
            if (staticContent.handle(exchange)) {
                exchange.close();
                return;
            }
        }

        exchange.sendResponseHeaders(404, 0);
        exchange.close();
    }

}

package org.actioncontrollerdemo.jdkhttp;

import com.sun.net.httpserver.HttpServer;
import org.actioncontroller.config.ConfigObserver;
import org.actioncontroller.httpserver.ApiHandler;
import org.actioncontrollerdemo.ContentSource;
import org.actioncontrollerdemo.TestController;
import org.actioncontrollerdemo.UserController;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;

public class DemoServer {
    private HttpServer httpServer;
    
    private Runnable updater = () -> System.out.println("Hello");
    private final ApiHandler apiHandler = new ApiHandler(new Object[]{
            new TestController(() -> updater.run()),
            new UserController()
    });
    private final StaticContent swaggerHandler = new StaticContent(ContentSource.fromWebJar("swagger-ui"));
    private final StaticContent staticContent = new StaticContent(getClass().getResource("/webapp-actioncontrollerdemo/"));
    private final RedirectHandler redirectHandler = new RedirectHandler("/demo");

    public void setUpdater(Runnable updater) {
        this.updater = updater;
    }

    public void setServerPort(InetSocketAddress inetSocketAddress) throws IOException {
        if (httpServer != null) {
            stop(1);
        }
        
        httpServer = HttpServer.create();
        httpServer.createContext("/demo/swagger", swaggerHandler);
        httpServer.createContext("/demo/api", apiHandler)
                .setAuthenticator(new DemoAuthenticator());
        httpServer.createContext("/demo", staticContent);
        httpServer.createContext("/", redirectHandler);
        httpServer.bind(inetSocketAddress, 0);
        httpServer.start();
    }

    public void setServerPort(int port) throws IOException {
        setServerPort(new InetSocketAddress("localhost", port));
    }

    public void stop(int delay) {
        httpServer.stop(delay);
    }

    public String getURL() {
        //return "http://" + httpServer.getAddress().getHostString() + ":" + httpServer.getAddress().getPort();
        return "http://" + "localhost" + ":" + httpServer.getAddress().getPort();
    }

    public static void main(String[] args) throws InterruptedException {
        DemoServer server = new DemoServer();
        ConfigObserver config = new ConfigObserver("demoserver");
        config.onInetSocketAddress("httpSocketAddress", 8080, server::setServerPort);
        System.out.println(server.getURL());
        DemoServer.class.wait();
    }

}

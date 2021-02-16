package org.actioncontrollerdemo.jdkhttp;

import com.sun.net.httpserver.HttpServer;
import org.actioncontroller.config.ConfigObserver;
import org.actioncontroller.httpserver.ApiHandler;
import org.actioncontrollerdemo.TestController;
import org.actioncontrollerdemo.UserController;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;

public class DemoServer {
    private HttpServer httpServer;
    
    private final WebjarContent handler = new WebjarContent("swagger-ui", "/demo/swagger");
    private final ApiHandler apiHandler = new ApiHandler(new Object[]{
            new TestController(() -> System.out.println("Hello")),
            new UserController()
    });
    private final StaticContent staticContent = new StaticContent(getClass().getResource("/webapp-actioncontrollerdemo"), "/demo");
    private final RedirectHandler redirectHandler = new RedirectHandler("/demo");

    public DemoServer() throws MalformedURLException {
    }

    public void setServerPort(InetSocketAddress inetSocketAddress) throws IOException {
        if (httpServer != null) {
            stop(1);
        }
        
        httpServer = HttpServer.create();
        httpServer.createContext("/demo/swagger", handler);
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

    public static void main(String[] args) throws IOException, InterruptedException {
        DemoServer server = new DemoServer();
        ConfigObserver config = new ConfigObserver("demoserver");
        config.onInetSocketAddress("httpSocketAddress", 8080, server::setServerPort);
        System.out.println(server.getURL());
        DemoServer.class.wait();
    }

}

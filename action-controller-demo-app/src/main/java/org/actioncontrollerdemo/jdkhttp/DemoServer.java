package org.actioncontrollerdemo.jdkhttp;

import com.sun.net.httpserver.HttpServer;
import org.actioncontroller.httpserver.ApiHandler;
import org.actioncontrollerdemo.TestController;
import org.actioncontrollerdemo.UserController;

import java.io.IOException;
import java.net.InetSocketAddress;

public class DemoServer {
    private final HttpServer httpServer;

    public DemoServer(InetSocketAddress inetSocketAddress) throws IOException {
        httpServer = HttpServer.create();
        httpServer.createContext("/demo/swagger", new WebjarContent("swagger-ui", "/demo/swagger"));
        httpServer.createContext("/demo/api", new ApiHandler(new Object[] {
                new TestController(() -> System.out.println("Hello")),
                new UserController()
        })).setAuthenticator(new DemoAuthenticator());
        httpServer.createContext("/demo", new StaticContent(getClass().getResource("/webapp-actioncontrollerdemo"), "/demo"));
        httpServer.createContext("/", new RedirectHandler("/demo"));
        httpServer.bind(inetSocketAddress, 0);
    }

    public DemoServer(int port) throws IOException {
        this(new InetSocketAddress("localhost", port));
    }

    public void start() {
        httpServer.start();
    }

    public void stop(int delay) {
        httpServer.stop(delay);
    }

    public String getURL() {
        //return "http://" + httpServer.getAddress().getHostString() + ":" + httpServer.getAddress().getPort();
        return "http://" + "localhost" + ":" + httpServer.getAddress().getPort();
    }

    public static void main(String[] args) throws IOException {
        DemoServer server = new DemoServer( 8080);
        server.start();
        System.out.println(server.getURL());
    }
}

package org.actioncontroller;

import com.sun.net.httpserver.HttpServer;
import org.actioncontroller.client.ApiClient;
import org.actioncontroller.client.HttpURLConnectionApiClient;
import org.actioncontroller.httpserver.ApiHandler;

import java.net.InetSocketAddress;

public class HttpPrincipalJdkServerTest extends AbstractHttpPrincipalTest {

    @Override
    protected ApiClient createApiClient(Object controller) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", new ApiHandler(controller));
        server.start();
        return new HttpURLConnectionApiClient("http://localhost:" + server.getAddress().getPort() + "/api");
    }
}

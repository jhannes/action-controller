package org.actioncontroller;

import com.sun.net.httpserver.HttpServer;
import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.client.HttpURLConnectionApiClient;
import org.actioncontroller.httpserver.ApiHandler;
import org.junit.Before;

import java.net.InetSocketAddress;

public class HttpPrincipalJdkServerTest extends AbstractHttpPrincipalTest {

    @Before
    public void createServerAndClient() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", new ApiHandler(new AuthenticatedController()));
        server.start();

        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        HttpURLConnectionApiClient apiClient = new HttpURLConnectionApiClient(baseUrl + "/api");
        client = ApiClientClassProxy.create(AuthenticatedController.class, apiClient);
    }
}

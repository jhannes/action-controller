package org.actioncontroller.json;

import com.sun.net.httpserver.HttpServer;
import org.actioncontroller.client.ApiClient;
import org.actioncontroller.client.HttpURLConnectionApiClient;
import org.actioncontroller.httpserver.ApiHandler;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.InetSocketAddress;

public class UnencryptedJsonCookieJdkServerTest extends UnencryptedJsonCookieTest {
    @Override
    protected ApiClient createClient(Controller controller) throws ServletException, IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", new ApiHandler(controller));
        server.start();
        return new HttpURLConnectionApiClient("http://localhost:" + server.getAddress().getPort() + "/api");
    }
}

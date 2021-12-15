package org.actioncontroller.httpserver;

import com.sun.net.httpserver.HttpServer;
import org.actioncontroller.AbstractApiClientProxyTest;
import org.actioncontroller.socket.SocketHttpClient;
import org.actioncontroller.client.ApiClient;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class ApiClientProxySocketsTest extends AbstractApiClientProxyTest {

    @Override
    protected ApiClient createClient(TestController controller) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new ApiHandler(new TestController()));
        server.start();
        return new SocketHttpClient(new URL("http://localhost:" + server.getAddress().getPort()));
    }

    @Test
    public void shouldConvertUrl() {
        assertThat(controllerClient.getPath(null).toString()).isEqualTo(apiClient.getBaseUrl().toString());
    }

    @Override
    public void shouldRethrowRuntimeExceptions() {

    }
}

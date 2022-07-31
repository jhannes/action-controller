package org.actioncontroller.socket;

import org.actioncontroller.AbstractApiClientProxyTest;
import org.actioncontroller.client.ApiClient;
import org.actioncontroller.client.HttpClientException;
import org.actioncontroller.client.HttpURLConnectionApiClient;
import org.junit.Test;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ApiClientProxySocketServerTest extends AbstractApiClientProxyTest {
    @Override
    protected ApiClient createClient(TestController controller) throws Exception {
        SocketHttpServer server = new SocketHttpServer(new InetSocketAddress("127.0.0.1", 0));
        server.createContext("/", new ApiSocketAdapter(new TestController()));
        server.start();
        return new HttpURLConnectionApiClient("http://localhost:" + server.getAddress());
    }

    @Override
    public void shouldRethrowRuntimeExceptions() {
        expectedLogEvents.expect(
                ApiSocketAdapter.class,
                Level.ERROR,
                "While handling SocketHttpExchange{GET [/someNiceMath]}",
                new ArithmeticException("/ by zero")
        );
        assertThatThrownBy(() -> controllerClient.divide(10, 0, false))
                .isInstanceOf(HttpClientException.class)
                .hasMessageContaining("Server Error");
    }

    @Test
    public void shouldConvertUrl() {
        assertThat(controllerClient.getPath(null).toString()).isEqualTo(apiClient.getBaseUrl().toString());
    }
}

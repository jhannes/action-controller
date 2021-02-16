package org.actioncontroller.httpserver;

import com.sun.net.httpserver.HttpServer;
import org.actioncontroller.AbstractApiClientProxyTest;
import org.actioncontroller.ApiControllerRouteMap;
import org.actioncontroller.SocketHttpClient;
import org.actioncontroller.client.ApiClient;
import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.client.HttpClientException;
import org.actioncontroller.client.HttpURLConnectionApiClient;
import org.junit.Test;
import org.slf4j.event.Level;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ApiClientProxyHttpServerTest extends AbstractApiClientProxyTest {

    private final ApiHandler handler = new ApiHandler(new TestController());

    @Override
    protected ApiClient createClient(TestController controller) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
        return new HttpURLConnectionApiClient("http://localhost:" + server.getAddress().getPort());
    }

    @Test
    public void shouldConvertUrl() {
        assertThat(controllerClient.getPath(null).toString()).isEqualTo(apiClient.getBaseUrl().toString());
    }

    @Test
    public void gives404OnUnmappedController() {
        expectedLogEvents.expectPattern(ApiControllerRouteMap.class, Level.INFO, "No route for {}. Routes {}");
        UnmappedController unmappedController = ApiClientClassProxy.create(UnmappedController.class, apiClient);
        assertThatThrownBy(unmappedController::notHere)
                .isInstanceOf(HttpClientException.class)
                .satisfies(e -> assertThat(((HttpClientException)e).getStatusCode()).isEqualTo(404));
    }

    @Test
    public void shouldCalculateUrlWithoutHost() throws IOException {
        URL url = new URL(apiClient.getBaseUrl() + "/loginSession/endsession");

        Socket socket = new Socket("localhost", url.getPort());
        socket.getOutputStream().write(("GET /loginSession/endsession HTTP/1.0\r\n" +
                "Connection: close\r\n" +
                "\r\n").getBytes());

        String responseLine = SocketHttpClient.readLine(socket.getInputStream());
        assertThat(responseLine).startsWith("HTTP/1.1 302 ");
        assertThat(SocketHttpClient.readHttpHeaders(socket.getInputStream()))
                .containsEntry("location", "http://" + Inet4Address.getByName("127.0.0.1").getHostName() + ":" + url.getPort() + "/frontPage");
    }

    @Override
    @Test
    public void shouldRethrowRuntimeExceptions() {
        expectedLogEvents.expect(
                ApiHandler.class,
                Level.ERROR,
                "While handling JdkHttpExchange{GET [/someNiceMath]}",
                new ArithmeticException("/ by zero")
        );
        assertThatThrownBy(() -> controllerClient.divide(10, 0, false))
                .isInstanceOf(HttpClientException.class)
                .hasMessageContaining("Server Error");
    }

    private long count = 0;
    @Test
    public void shouldCountExecutions() {
        handler.setTimerRegistry(name -> duration -> count++);
        controllerClient.first();
        assertThat(count).isEqualTo(1);
    }

}

package org.actioncontroller.httpserver;

import com.sun.net.httpserver.HttpServer;
import org.actioncontroller.AbstractApiClientProxyTest;
import org.actioncontroller.HttpActionException;
import org.actioncontroller.SocketHttpClient;
import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.client.HttpClientException;
import org.actioncontroller.client.HttpURLConnectionApiClient;
import org.junit.Before;
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

    @Before
    public void createServerAndClient() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new ApiHandler(new TestController()));
        server.start();

        baseUrl = "http://localhost:" + server.getAddress().getPort();
        client = ApiClientClassProxy.create(TestController.class,
                new HttpURLConnectionApiClient(baseUrl));
    }

    @Test
    public void gives404OnUnmappedController() {
        expectedLogEvents.expect(ApiHandler.class, Level.INFO, "No route for GET [/not-mapped]");
        UnmappedController unmappedController = ApiClientClassProxy.create(UnmappedController.class,
                        new HttpURLConnectionApiClient(baseUrl));
        assertThatThrownBy(unmappedController::notHere)
                .isInstanceOf(HttpClientException.class)
                .satisfies(e -> assertThat(((HttpClientException)e).getStatusCode()).isEqualTo(404));
    }

    @Test
    public void shouldCalculateUrlWithoutHost() throws IOException {
        URL url = new URL(baseUrl + "/loginSession/endsession");

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
                "While handling JdkHttpExchange{GET [/someNiceMath]} with ApiControllerMethodAction{GET /someNiceMath => TestController.divide(int,int,boolean)}",
                new ArithmeticException("/ by zero")
        );
        assertThatThrownBy(() -> client.divide(10, 0, false))
                .isInstanceOf(HttpClientException.class)
                .hasMessageContaining("Server Error");
    }

}

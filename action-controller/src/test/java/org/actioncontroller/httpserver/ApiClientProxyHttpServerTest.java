package org.actioncontroller.httpserver;

import com.sun.net.httpserver.HttpServer;
import org.actioncontroller.AbstractApiClientProxyTest;
import org.actioncontroller.HttpActionException;
import org.actioncontroller.client.ApiClientProxy;
import org.actioncontroller.client.HttpURLConnectionApiClient;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.event.Level;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ApiClientProxyHttpServerTest extends AbstractApiClientProxyTest {

    private String baseUrl;

    @Before
    public void createServerAndClient() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", new ApiHandler(new TestController()));
        server.start();

        baseUrl = "http://localhost:" + server.getAddress().getPort();
        client = ApiClientProxy.create(TestController.class,
                new HttpURLConnectionApiClient(baseUrl));
    }

    @Test
    public void gives404OnUnmappedController() throws MalformedURLException {
        expectedLogEvents.expect(ApiHandler.class, Level.WARN, "No route for JdkHttpExchange{GET [/not-mapped]}");
        UnmappedController unmappedController = ApiClientProxy.create(UnmappedController.class,
                        new HttpURLConnectionApiClient(baseUrl));
        assertThatThrownBy(unmappedController::notHere)
                .isInstanceOf(HttpActionException.class)
                .satisfies(e -> assertThat(((HttpActionException)e).getStatusCode()).isEqualTo(404));
    }
}

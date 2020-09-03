package org.actioncontroller.httpserver;

import com.sun.net.httpserver.HttpServer;
import org.actioncontroller.AbstractApiClientProxyTest;
import org.actioncontroller.SocketHttpClient;
import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.client.HttpClientException;
import org.actioncontroller.client.HttpURLConnectionApiClient;
import org.actioncontroller.servlet.ApiControllerActionRouter;
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

public class ApiClientProxySocketsTest extends AbstractApiClientProxyTest {

    @Before
    public void createServerAndClient() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new ApiHandler(new TestController()));
        server.start();

        baseUrl = "http://localhost:" + server.getAddress().getPort();
        client = ApiClientClassProxy.create(TestController.class,
                new SocketHttpClient(baseUrl));
    }


    @Override
    public void shouldRethrowRuntimeExceptions() {

    }
}

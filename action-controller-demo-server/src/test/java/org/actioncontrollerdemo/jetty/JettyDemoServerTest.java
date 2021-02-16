package org.actioncontrollerdemo.jetty;

import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.client.HttpURLConnectionApiClient;
import org.actioncontrollerdemo.UserController;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class JettyDemoServerTest {

    private JettyDemoServer server;
    private HttpURLConnectionApiClient client;

    @Before
    public void setUp() throws Exception {
        server = new JettyDemoServer();
        server.startConnector(0);
        client = new HttpURLConnectionApiClient("http://localhost:" + server.getPort() + "/demo/api");
    }

    @Test
    public void shouldStartSuccessfully() throws Exception {
        URL url = new URL(server.getUrl() + "/demo");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        assertThat(connection.getResponseCode())
                .as(connection.getResponseMessage())
                .isEqualTo(200);
        String body = HttpURLConnectionApiClient.asString(connection.getInputStream());
        assertThat(body).contains("<h1>Hello World</h1>");
    }
    
    @Test
    public void shouldReturn404OnUnknownFile() throws IOException {
        URL url = new URL(server.getUrl() + "/demo/missing");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        assertThat(connection.getResponseCode())
                .as(connection.getResponseMessage())
                .isEqualTo(404);
    }

    @Test
    public void shouldAuthenticateUser() {
        UserController userApi = ApiClientClassProxy.create(UserController.class, client);
        userApi.postLogin("john doe", Optional.empty(), new AtomicReference<String>()::set);
        assertThat(userApi.getRealUsername(null)).isEqualTo("Hello - required, john doe");
    }
}

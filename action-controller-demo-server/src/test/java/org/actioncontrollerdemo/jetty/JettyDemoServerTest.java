package org.actioncontrollerdemo.jetty;

import org.actioncontroller.client.HttpURLConnectionApiClient;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class JettyDemoServerTest {

    @Test
    public void shouldStartSuccessfully() throws Exception {
        JettyDemoServer server = new JettyDemoServer();
        server.startConnector(0);

        URL url = new URL(server.getUrl() + "/demo");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        assertThat(connection.getResponseCode())
                .as(connection.getResponseMessage())
                .isEqualTo(200);
        String body = HttpURLConnectionApiClient.asString(connection.getInputStream());
        assertThat(body).contains("<h1>Hello World</h1>");
    }
}

package org.actioncontrollerdemo.jdkhttp;

import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.client.HttpURLConnectionApiClient;
import org.actioncontrollerdemo.TestController;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class DemoServerTest {

    private DemoServer server;

    @Before
    public void setUp() throws Exception {
        server = new DemoServer(0);
        server.start();
    }

    @Test
    public void shouldShowFrontPage() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(server.getURL()).openConnection();
        assertThat(connection.getResponseCode()).isEqualTo(200);
        StringWriter writer = new StringWriter();
        new BufferedReader(new InputStreamReader(connection.getInputStream())).transferTo(writer);
        assertThat(writer.toString())
                .contains("<h1>Hello World</h1>");
    }

    @Test
    public void shouldShowJson() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(server.getURL() + "/demo/openapi.yaml").openConnection();
        assertThat(connection.getResponseCode()).isEqualTo(200);
        StringWriter writer = new StringWriter();
        new BufferedReader(new InputStreamReader(connection.getInputStream())).transferTo(writer);
        assertThat(writer.toString())
                .contains("Swagger Petstore");
    }

    @Test
    public void shouldCallTestController() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(server.getURL()).openConnection();
        assertThat(connection.getResponseCode()).isEqualTo(200);

        TestController testController = ApiClientClassProxy.create(TestController.class, new HttpURLConnectionApiClient(server.getURL() + "/demo/api"));
        assertThat(testController.sayHello(Optional.of("Test")))
                .isEqualTo("Hello Test");
    }
}

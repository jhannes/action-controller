package org.actioncontrollerdemo.jetty;

import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.client.HttpURLConnectionApiClient;
import org.actioncontrollerdemo.UserController;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.optional.junit.ExpectedLogEventsRule;
import org.slf4j.event.Level;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class JettyDemoServerTest {

    private JettyDemoServer server;
    private HttpURLConnectionApiClient client;

    @Rule
    public ExpectedLogEventsRule expectedLogEvents = new ExpectedLogEventsRule(Level.WARN);

    @Before
    public void setUp() throws Exception {
        server = new JettyDemoServer();
        expectedLogEvents.expectPattern(JettyDemoServer.class, Level.WARN, "Listening on {}");
        server.startConnector(0);
        client = new HttpURLConnectionApiClient("http://localhost:" + server.getPort() + "/demo/api");
    }

    @Test
    public void shouldReturnWebContent() throws Exception {
        URL url = new URL(server.getUrl() + "/demo");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        assertThat(connection.getResponseCode())
                .as(connection.getResponseMessage())
                .isEqualTo(200);
        String body = HttpURLConnectionApiClient.asString(connection.getInputStream());
        assertThat(body).contains("<h1>Demo Action Controller</h1>");
        assertThat(connection.getHeaderField("Content-Type")).isEqualTo("text/html");

        assertThat(ZonedDateTime.parse(connection.getHeaderField("Last-Modified"), DateTimeFormatter.RFC_1123_DATE_TIME))
                .isBefore(ZonedDateTime.now().minusSeconds(1))
                .as("last modified should be more than one second ago or something is fishy");
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
    public void shouldReturnContentFromWebJar() throws IOException {
        URL url = new URL(server.getUrl() + "/demo/swagger/");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        assertThat(connection.getResponseCode())
                .as(connection.getResponseMessage())
                .isEqualTo(200);
        String body = HttpURLConnectionApiClient.asString(connection.getInputStream());
        assertThat(body).contains("swagger-initializer.js");
        assertThat(connection.getHeaderField("Content-Type")).isEqualTo("text/html");
        long lastModified = connection.getHeaderFieldDate("Last-Modified", -1);
        assertThat(lastModified).isNotEqualTo(-1);
        assertThat(System.currentTimeMillis() - lastModified)
                .as("last modified should be more than five seconds ago or something is fishy")
                .isGreaterThan(5000);
    }

    @Test
    public void shouldReturn404OnMissingFileInWebjar() throws IOException {
        URL url = new URL(server.getUrl() + "/demo/swagger/missing.html");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        assertThat(connection.getResponseCode())
                .as(connection.getResponseMessage())
                .isEqualTo(404);
    }

    @Test
    public void shouldReturn304OnFileNotModified() throws IOException {
        URL url = new URL(server.getUrl() + "/demo/swagger/");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("If-Modified-Since", "Sun, 06 Oct 2030 02:53:46 GMT");
        assertThat(connection.getResponseCode())
                .as(connection.getResponseMessage())
                .isEqualTo(304);
    }

    @Test
    public void shouldAuthenticateUser() {
        UserController userApi = ApiClientClassProxy.create(UserController.class, client);
        userApi.postLogin("john doe", Optional.empty(), new AtomicReference<String>()::set);
        //noinspection ConstantConditions
        assertThat(userApi.getRealUsername(null)).isEqualTo("Hello - required, john doe");
    }
}

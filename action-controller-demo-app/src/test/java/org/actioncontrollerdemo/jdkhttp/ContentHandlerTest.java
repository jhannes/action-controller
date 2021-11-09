package org.actioncontrollerdemo.jdkhttp;

import com.sun.net.httpserver.HttpServer;
import org.actioncontroller.content.ContentSource;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class ContentHandlerTest {

    private Path dir;
    private HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);

    public ContentHandlerTest() throws IOException {
    }

    @Before
    public void setUp() throws Exception {
        dir = Paths.get("target/test-data/" + UUID.randomUUID());
        Files.createDirectories(dir);
        server.createContext("/", new ContentHandler(ContentSource.fromURL(dir.toUri().toURL())));
        server.start();
    }

    @Test
    public void shouldReturnContentWithTimeStamp() throws IOException {
        Path file = dir.resolve("index.html");
        Files.write(file, List.of("Hello World"));

        ZonedDateTime lastModified = ZonedDateTime.of(LocalDate.of(2021, 12, 1), LocalTime.of(3, 14), ZoneId.of("CET"));
        Files.setLastModifiedTime(file, FileTime.from(lastModified.toInstant()));

        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + server.getAddress().getPort()).openConnection();

        assertThat(conn.getResponseCode()).isEqualTo(200);
        assertThat(conn.getHeaderField("Last-modified"))
                .isEqualTo("Wed, 1 Dec 2021 03:14:00 +0100");
    }

    @Test
    public void shouldReturnNotModifiedIfContentIsUpdated() throws IOException {
        Path file = dir.resolve("index.html");
        Files.write(file, List.of("Hello World"));

        ZonedDateTime lastModified = ZonedDateTime.of(LocalDate.of(2021, 12, 1), LocalTime.of(3, 14), ZoneId.of("CET"));
        Files.setLastModifiedTime(file, FileTime.from(lastModified.toInstant()));

        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + server.getAddress().getPort()).openConnection();
        conn.setIfModifiedSince(lastModified.minusHours(1).toEpochSecond()*1000);

        assertThat(conn.getResponseCode()).isEqualTo(304);
        assertThat(conn.getInputStream()).isEmpty();
    }

    @Test
    public void shouldReturnNotFoundIfContentIsMissing() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + server.getAddress().getPort()).openConnection();
        assertThat(conn.getResponseCode()).isEqualTo(404);
        assertThat(conn.getErrorStream()).isEmpty();
    }

}

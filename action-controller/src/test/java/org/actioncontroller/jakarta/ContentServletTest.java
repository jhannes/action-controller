package org.actioncontroller.jakarta;

import jakarta.servlet.ServletException;
import org.actioncontroller.content.ContentSource;
import org.actioncontroller.jakarta.ContentServlet;
import org.fakeservlet.jakarta.FakeJakartaConfig;
import org.fakeservlet.jakarta.FakeJakartaContainer;
import org.fakeservlet.jakarta.FakeJakartaRequest;
import org.fakeservlet.jakarta.FakeJakartaResponse;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.actioncontroller.content.Content.RFC_1123_DATE_TIME;
import static org.assertj.core.api.Assertions.assertThat;

public class ContentServletTest {

    private Path dir;
    private final FakeJakartaContainer container = new FakeJakartaContainer();
    private ContentServlet servlet;

    @Before
    public void setUp() throws Exception {
        dir = Paths.get("target/test-data/" + UUID.randomUUID());
        Files.createDirectories(dir);
        servlet = new ContentServlet(ContentSource.fromURL(dir.toUri().toURL()));
    }

    @Test
    public void shouldReturnContentWithTimeStamp() throws ServletException, IOException {
        Path file = dir.resolve("index.html");
        Files.write(file, List.of("Hello World"));

        ZonedDateTime lastModified = ZonedDateTime.of(LocalDate.of(2021, 12, 1), LocalTime.of(3, 14), ZoneId.of("CET"));
        Files.setLastModifiedTime(file, FileTime.from(lastModified.toInstant()));

        servlet.init(new FakeJakartaConfig());

        FakeJakartaRequest request = container.newRequest("GET", "/index.html");
        FakeJakartaResponse response = request.service(servlet);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Last-modified"))
                .isEqualTo(lastModified.format(RFC_1123_DATE_TIME));
    }

    @Test
    public void shouldReturnNotModifiedIfContentIsUpdated() throws IOException, ServletException {
        Path file = dir.resolve("index.html");
        Files.write(file, List.of("Hello World"));

        ZonedDateTime lastModified = ZonedDateTime.of(LocalDate.of(2021, 12, 1), LocalTime.of(3, 14), ZoneId.of("CET"));
        Files.setLastModifiedTime(file, FileTime.from(lastModified.toInstant()));

        FakeJakartaRequest request = container.newRequest("GET", "/index.html");
        request.addHeader("If-Modified-Since", DateTimeFormatter.RFC_1123_DATE_TIME.format(lastModified.plusHours(1)));
        FakeJakartaResponse response = request.service(servlet);

        assertThat(response.getStatus()).isEqualTo(304);
        assertThat(response.getBodyString()).isEmpty();
    }

    @Test
    public void shouldReturnNotFoundIfContentIsMissing() throws IOException, ServletException {
        Path file = dir.resolve("index.html");

        FakeJakartaRequest request = container.newRequest("GET", "/index.html");
        FakeJakartaResponse response = request.service(servlet);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getBodyString()).isEmpty();
    }
}

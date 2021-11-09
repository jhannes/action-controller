package org.actioncontrollerdemo.jetty.servlet;

import org.actioncontroller.content.ContentSource;
import org.fakeservlet.FakeServletConfig;
import org.fakeservlet.FakeServletContainer;
import org.fakeservlet.FakeServletRequest;
import org.fakeservlet.FakeServletResponse;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.ServletException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class ContentServletTest {

    private Path dir;
    private FakeServletContainer container = new FakeServletContainer();
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

        servlet.init(new FakeServletConfig());

        FakeServletRequest request = container.newRequest("GET", "/index.html");
        FakeServletResponse response = request.service(servlet);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Last-modified"))
                .isEqualTo("Wed, 1 Dec 2021 03:14:00 +0100");
    }

    @Test
    public void shouldReturnNotModifiedIfContentIsUpdated() throws IOException, ServletException {
        Path file = dir.resolve("index.html");
        Files.write(file, List.of("Hello World"));

        ZonedDateTime lastModified = ZonedDateTime.of(LocalDate.of(2021, 12, 1), LocalTime.of(3, 14), ZoneId.of("CET"));
        Files.setLastModifiedTime(file, FileTime.from(lastModified.toInstant()));

        FakeServletRequest request = container.newRequest("GET", "/index.html");
        request.addHeader("If-Modified-Since", DateTimeFormatter.RFC_1123_DATE_TIME.format(lastModified.minusHours(1)));
        FakeServletResponse response = request.service(servlet);

        assertThat(response.getStatus()).isEqualTo(304);
        assertThat(response.getBodyString()).isEmpty();
    }

    @Test
    public void shouldReturnNotFoundIfContentIsMissing() throws IOException, ServletException {
        Path file = dir.resolve("index.html");

        FakeServletRequest request = container.newRequest("GET", "/index.html");
        FakeServletResponse response = request.service(servlet);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getBodyString()).isEmpty();
    }
}

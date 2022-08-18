package org.actioncontroller.multipart;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.actioncontroller.httpserver.ApiHandler;
import org.jsonbuddy.JsonArray;
import org.jsonbuddy.JsonObject;
import org.jsonbuddy.parse.JsonParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MultipartExampleControllerTest {
    private MultipartExampleController controllerUnderTest;
    private HttpServer server;
    private String prefix;

    private final File testFilePdf = new File("target/test/multipart-test-file.pdf");
    private final File testFileTxt = new File("target/test/poem.txt");
    private final String testPdfResourceFile = "/multipart/w3-wai-er-dummy.pdf";
    private final String testTextFileResourceFile = "/multipart/poem.txt";

    @Before
    public void prepareForTest() throws IOException {
        controllerUnderTest = new MultipartExampleController();
        startHttpServerWithAuthenticatedStudiestedbrukerForController();
    }

    private void startHttpServerWithAuthenticatedStudiestedbrukerForController() throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
            "/",
            new ApiHandler(controllerUnderTest) {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    super.handle(exchange);
                }
            }
        );
        server.start();
        this.server = server;
        this.prefix = "http://localhost:"+ server.getAddress().getPort();
    }

    @After
    public void close() {
        server.stop(0);
    }

    @Test
    public void testHandleMultipart() throws IOException, InterruptedException {

        ensureTestFilesIsAvailable();
        var multipartData = new ArrayList<Pair>();
        multipartData.add(new Pair("navn", "Paul"));
        multipartData.add(new Pair("house", "House of Atreides"));
        multipartData.add(new Pair("diploma", testFilePdf));
        multipartData.add(new Pair("diploma", testFileTxt));

        var testTarget =
            URI.create(prefix + MultipartExampleController.MULTIPART_POST_PATH);
        var boundary = UUID.randomUUID().toString() + "-boundary";
        var request = HttpRequest.newBuilder(testTarget)
            .header("Accept", "application/json")
            .header("Content-Type", "multipart/form-data;boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArrays(buildMultipartFormDataPayload(boundary, multipartData)))
            .build();
        var response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        var jsonResponse = JsonParser.parse(response.body());
        JsonArray files = new JsonArray();
        files.add(
            new JsonObject().put(
                testFilePdf.getName(),
                new String(Base64.getEncoder().encode(Files.readAllBytes(testFilePdf.toPath())))
            ));
        files.add(new JsonObject().put(
            testFileTxt.getName(),
            new String(Base64.getEncoder().encode(Files.readAllBytes(testFileTxt.toPath())))
        ));
        assertThat(jsonResponse).isEqualTo(
            new JsonObject()
                .put("navn", "Paul")
                .put("house", "House of Atreides")
                .put("diploma", files)
        );
    }

    public static class Pair {

        private final String fieldName;
        private final Object data;

        public Pair(String fieldName, Object data) {
            this.fieldName = fieldName;
            this.data = data;
        }

    }

    private void ensureTestFilesIsAvailable() throws IOException {
        ensureTestResourceIsAvailable(testFilePdf, getClasspathResourceInputStream(testPdfResourceFile));
        ensureTestResourceIsAvailable(testFileTxt, getClasspathResourceInputStream(testTextFileResourceFile));
    }

    private void ensureTestResourceIsAvailable(final File file, InputStream testResourceAvailableOnClasspath)
        throws IOException {
        if (file.isFile()) {
            // Lets not write to disk if not needed.
            return;
        }
        var path = file.toPath();
        Files.copy(testResourceAvailableOnClasspath, path);
        assertThat(Files.readAllBytes(path)).isNotNull();
    }

    private InputStream getClasspathResourceInputStream(String testResource) {
        var is = this.getClass().getResourceAsStream(testResource);
        if (is == null) {
            throw new IllegalStateException("Could not read test file");
        }
        return is;
    }


    private ArrayList<byte[]> buildMultipartFormDataPayload(
        String boundary,
        List<Pair> data
    ) throws IOException {
        var byteArrays = new ArrayList<byte[]>();
        var separator = ("--"+boundary+"\r\nContent-Disposition: form-data; name=").getBytes(StandardCharsets.UTF_8);

        for (Pair entry : data) {
            byteArrays.add(separator);
            if (entry.data instanceof File) {
                var file = (File) entry.data;
                var path = Path.of(file.toURI());
                var mimeType = Files.probeContentType(path);
                if (mimeType == null) {
                    mimeType = "application/octet-stream";
                }

                byteArrays.add(
                    ("\"" + entry.fieldName + "\"; filename=\"" + path.getFileName()
                        + "\"\r\nContent-Type: " + mimeType
                        + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                byteArrays.add(Files.readAllBytes(path));
                byteArrays.add("\r\n".getBytes(StandardCharsets.UTF_8));
            } else {
                byteArrays.add(("\"" + entry.fieldName + "\"\r\n\r\n" + entry.data + "\r\n")
                    .getBytes(StandardCharsets.UTF_8)
                );
            }
        }
        byteArrays.add(("--"+boundary+"--").getBytes(StandardCharsets.UTF_8));
        byteArrays.add("\r\n".getBytes(StandardCharsets.UTF_8));
        return byteArrays;
    }
}
package org.actioncontroller;

import org.actioncontroller.actions.GET;
import org.actioncontroller.actions.POST;
import org.actioncontroller.actions.PUT;
import org.actioncontroller.client.ApiClient;
import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.client.HttpClientException;
import org.actioncontroller.exceptions.HttpActionException;
import org.actioncontroller.exceptions.HttpNotModifiedException;
import org.actioncontroller.exceptions.HttpRequestException;
import org.actioncontroller.values.ContentBody;
import org.actioncontroller.values.ContentLocationHeader;
import org.actioncontroller.values.ContextUrl;
import org.actioncontroller.values.HttpHeader;
import org.actioncontroller.values.HttpResponseHeader;
import org.actioncontroller.values.IfModifiedSince;
import org.actioncontroller.values.LastModified;
import org.actioncontroller.values.PathParam;
import org.actioncontroller.values.RequestParam;
import org.actioncontroller.values.SendRedirect;
import org.actioncontroller.values.UnencryptedCookiePreview;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.optional.junit.ExpectedLogEventsRule;
import org.slf4j.event.Level;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.annotation.RetentionPolicy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings({"ConstantConditions", "OptionalAssignedToNull"})
public abstract class AbstractApiClientProxyTest {

    protected ApiClient apiClient;

    public enum Number {
        ONE("number one", 1),
        TWO("number two", 2),
        THREE("number three", 3);

        private final String text;
        public final int value;

        Number(String text, int value) {
            this.text = text;
            this.value = value;
        }

        public String toString() {
            return text;
        }
    }

    public static class TestController {

        @GET("/")
        @ContentBody
        public String first() {
            return "Hello world";
        }

        @GET("/?greeting")
        @ContentBody
        public String first(@RequestParam("greeting") String greeting, ApiHttpExchange exchange) {
            return greeting + " " + exchange.getPathInfo();
        }

        @GET("/stream")
        @ContentBody
        public BufferedInputStream getStream() {
            return new BufferedInputStream(new ByteArrayInputStream("hello world".getBytes()));
        }

        @GET("/stream/withCache")
        @ContentBody
        public BufferedInputStream getStreamWithTimestamps(
                @IfModifiedSince Optional<ZonedDateTime> ifModifiedSince,
                @LastModified Consumer<ZonedDateTime> setLastModified
        ) {
            ZonedDateTime lastModified = ZonedDateTime.of(2020, 1, 12, 11, 12, 15, 5000, ZoneId.systemDefault());
            if (ifModifiedSince.map(lastModified::isAfter).orElse(false)) {
                throw new HttpNotModifiedException(lastModified);
            }
            setLastModified.accept(lastModified);
            return new BufferedInputStream(new ByteArrayInputStream("hello world".getBytes()));
        }

        @GET("/reader")
        @ContentBody
        public BufferedReader getReader() {
            return new BufferedReader(new StringReader("Hello World"));
        }

        @POST("/uppercase")
        @ContentBody
        public String upcase(
                @ContentBody String parameter,
                @HttpHeader("Content-characters") Consumer<Integer> setContentCharacters
        ) {
            setContentCharacters.accept(parameter.length());
            return parameter.toUpperCase();
        }

        @GET("/someNiceMath")
        @ContentBody
        public int divide(@RequestParam("whole") int whole, @RequestParam("divisor") int divisor, @RequestParam("remainder") boolean remainder) {
            return remainder ? whole % divisor : whole / divisor;
        }

        @GET("/enumText")
        @ContentBody
        public String enumText(@RequestParam("policy") Optional<RetentionPolicy> policy) {
            return policy.map(Enum::name).orElse("<none>");
        }

        @POST("/resource/:id")
        @ContentLocationHeader
        public String storeNewEntry(@PathParam("id") String id) {
            return "/entries/" + id;
        }

        @POST("/newResource")
        @ContentLocationHeader("/resource/{resourceId}/data")
        public UUID createNewResource(@RequestParam("id") UUID id) {
            return id;
        }

        @GET("/person/{personId}")
        @ContentBody
        public String getName(@PathParam("personId") String personId) {
            return personId + "'s name";
        }

        @PUT("/loginSession/me")
        public void putLoginSession(
                @RequestParam("username") String username,
                @RequestParam("password") String password,
                @UnencryptedCookiePreview("sessionCookie") Consumer<String> sessionCookie
        ) {
            sessionCookie.accept(username + ":" + password);
        }

        @GET("/loginSession/me")
        @HttpResponseHeader("X-Username")
        public String whoAmI(@UnencryptedCookiePreview("sessionCookie") Optional<String> sessionCookie) {
            return sessionCookie.map(s -> s.split(":")[0]).orElse("<none>");
        }

        @GET("/loginSession/me/required")
        @HttpResponseHeader("X-Username")
        public String whoAmIRequired(@UnencryptedCookiePreview("sessionCookie") String sessionCookie) {
            return sessionCookie.split(":")[0];
        }

        @GET("/loginSession/endsession")
        @SendRedirect
        public String endsession(@UnencryptedCookiePreview("sessionCookie") Consumer<String> setSessionCookie, @ContextUrl String url) {
            setSessionCookie.accept(null);
            return url + "/frontPage";
        }

        @POST("/loginSession/changeUser")
        @ContentBody
        public String changeUser(
                @UnencryptedCookiePreview("username") AtomicReference<String> usernameCookie,
                @RequestParam("username") String newUsername
        ) {
            String oldValue = usernameCookie.get();
            usernameCookie.set(newUsername);
            return oldValue;
        }

        @GET("/explicitError")
        @ContentBody
        public String explicitError() {
            throw new HttpActionException(403, "You're not allowed to do this");
        }

        @GET("/lowercase")
        @HttpHeader("X-Result")
        public String downcase(@HttpHeader("X-Input") Optional<String> value) {
            return value.orElse("").toLowerCase();
        }

        @GET("/lowercaseAll")
        @ContentBody
        public String downcaseAll(@RequestParam("value") Optional<List<String>> value) {
            return value
                    .map(list -> list.stream().map(String::toLowerCase).collect(Collectors.joining(", ")))
                    .orElse("<none>");
        }

        @GET("/sum")
        @ContentBody
        public int sum(@RequestParam("values") Optional<List<Integer>> value) {
            int sum = 0;
            for (Integer num : value.orElse(new ArrayList<>())) {
                sum += num;
            }
            return sum;
        }

        @GET("/enumSum")
        @ContentBody
        public int enumSum(@RequestParam("values") Optional<List<Number>> value) {
            int sum = 0;
            for (Number num : value.orElse(new ArrayList<>())) {
                sum += num.value;
            }
            return sum;
        }

        @POST("/reverseBytes")
        @ContentBody
        public byte[] reverseBytes(@ContentBody byte[] bytes) {
            byte[] result = new byte[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                result[result.length - i - 1] = bytes[i];
            }
            return result;
        }

        @GET("/image.png")
        @ContentBody
        public byte[] getImage(@HttpHeader("Content-Type") Consumer<String> setContentType) {
            setContentType.accept("image/png");
            return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAAHElEQVQI12P4//8/w38GIAXDIBKE0DHxgljNBAAO9TXL0Y4OHwAAAABJRU5ErkJggg==");
        }

        @GET("/error")
        @ContentBody
        public String sendError(@RequestParam("errorMessage") String errorMessage) {
            throw new HttpRequestException(errorMessage);
        }

        @GET("/htmlError")
        @ContentBody(contentType = "text/html")
        public String sendHtmlError(@RequestParam("errorMessage") String errorMessage) {
            throw new HttpRequestException(errorMessage);
        }

        @GET("/files/{filename}.html")
        @ContentBody(contentType = "text/html")
        public String getHtmlFile(@PathParam("filename") String filename) {
            return "<html><h2>Hello from " + filename + "</h2></html>";
        }

        @SendRedirect
        @GET("/path")
        public URL getPath(@ContextUrl URL url) {
            return url;
        }

        @POST("/sendRedirect")
        public void sendRedirect(@HttpHeader("Content-Location") Consumer<URI> setContentLocation) throws URISyntaxException {
            setContentLocation.accept(new URI("https://github.com/jhannes"));
        }

        @GET("/files/:filename.txt")
        @ContentBody()
        public String getTextFile(@PathParam("filename") String filename) {
            return "Hello from " + filename;
        }
    }

    public static class UnmappedController {
        @GET("/not-mapped")
        @ContentBody
        public String notHere() {
            return "Not here";
        }
    }

    protected TestController controllerClient;


    @Rule
    public ExpectedLogEventsRule expectedLogEvents = new ExpectedLogEventsRule(Level.WARN);

    @Before
    public void createServerAndClient() throws Exception {
        final TestController controller = new TestController();
        apiClient = createClient(controller);
        this.controllerClient = ApiClientClassProxy.create(TestController.class, apiClient);
    }

    protected abstract ApiClient createClient(TestController controller) throws Exception;

    @Test
    public void shouldMakeSimpleHttpGet() {
        assertThat(controllerClient.first()).isEqualTo("Hello world");
    }

    @Test
    public void shouldRouteWithRequiredParameter() {
        String first = controllerClient.first("Hi!", null);
        assertThat(first).isEqualTo("Hi! /");
    }


    @Test
    public void shouldReceiveParameters() {
        AtomicInteger value = new AtomicInteger();
        assertThat(controllerClient.upcase("Test string", value::set))
                .isEqualTo("Test string".toUpperCase());
        assertThat(value.get()).isEqualTo("Test string".length());
    }

    @Test
    public void shouldConvertParameters() {
        assertThat(controllerClient.divide(8, 2, false)).isEqualTo(4);
    }

    @Test
    public void shouldConvertEnums() {
        assertThat(controllerClient.enumText(Optional.of(RetentionPolicy.SOURCE))).isEqualTo("SOURCE");
    }

    @Test
    public void shouldConvertUri() throws URISyntaxException {
        AtomicReference<URI> contentLocation = new AtomicReference<>();
        controllerClient.sendRedirect(contentLocation::set);
        assertThat(contentLocation.get()).isEqualTo(new URI("https://github.com/jhannes"));
    }

    @Test
    public void shouldSupportMultipleArguments() {
        assertThat(controllerClient.downcaseAll(Optional.of(Arrays.asList("A", "B", "c"))))
                .isEqualTo("a, b, c");
        assertThat(controllerClient.downcaseAll(Optional.empty()))
                .isEqualTo("<none>");
        assertThat(controllerClient.downcaseAll(Optional.of(Collections.singletonList(""))))
                .isEqualTo("<none>");
    }

    @Test
    public void shouldConvertMultipleArguments() {
        assertThat(controllerClient.sum(Optional.of(Arrays.asList(1, 2, 3, 4))))
                .isEqualTo(10);
        assertThat(controllerClient.sum(Optional.empty()))
                .isEqualTo(0);
    }

    @Test
    public void shouldConvertMultipleEnums() {
        assertThat(controllerClient.enumSum(Optional.of(Arrays.asList(Number.ONE, Number.TWO, Number.THREE))))
                .isEqualTo(6);
    }

    @Test
    public void shouldConvertUrl() {
        assertThat(controllerClient.getPath(null).toString() + "/api")
                .isEqualTo(apiClient.getBaseUrl().toString());
    }

    @Test
    public void shouldOmitEmptyOptional() {
        assertThat(controllerClient.enumText(Optional.empty())).isEqualTo("<none>");
    }

    @Test
    public void shouldReportActionExceptions() {
        assertThatThrownBy(() -> controllerClient.explicitError())
                .isEqualTo(new HttpClientException(403, "Forbidden", "You're not allowed to do this", null))
                .satisfies(e -> assertThat(((HttpClientException) e).getResponseBody()).contains("You're not allowed to do this"))
                .satisfies(e -> assertThat(((HttpClientException) e).getUrl().toString()).endsWith("/explicitError"));
    }

    @Test
    public abstract void shouldRethrowRuntimeExceptions();

    @Test
    public void shouldHandleContentHeaders() {
        assertThat(controllerClient.storeNewEntry("someUrl")).endsWith("/entries/" + "someUrl");
    }

    @Test
    public void shouldHandleArgumentsInContentHeaders() {
        UUID id = UUID.randomUUID();
        assertThat(controllerClient.createNewResource(id)).isEqualTo(id);
    }

    @Test
    public void shouldHandlePathParams() {
        assertThat(controllerClient.getName("SomePerson")).isEqualTo("SomePerson's name");
    }

    private String sessionCookie;

    @Test
    public void shouldSetCookies() {
        controllerClient.putLoginSession("username", "let-me-in", s -> sessionCookie = s);
        assertThat(sessionCookie).isEqualTo("username:let-me-in");
    }

    @Test
    public void shouldReadCookies() {
        assertThat(controllerClient.whoAmI(Optional.of("someUser:let-me-in"))).isEqualTo("someUser");
    }

    @Test
    public void shouldEndSession() {
        controllerClient.putLoginSession("the user", "let-me-in", null);
        assertThat(controllerClient.whoAmI(null)).isEqualTo("the user");
        String redirectUrl = controllerClient.endsession(null, null);
        assertThat(redirectUrl).isEqualTo(apiClient.getBaseUrl().toString().replaceAll("/api$", "") + "/frontPage");
        assertThat(controllerClient.whoAmI(null)).isEqualTo("<none>");
    }

    @Test
    public void shouldRequireCookie() {
        controllerClient.putLoginSession("the user", "let-me-in", null);
        assertThat(controllerClient.whoAmIRequired(null)).isEqualTo("the user");
        controllerClient.endsession(null, null);
        assertThatThrownBy(() -> controllerClient.whoAmIRequired(null))
                .isInstanceOf(HttpClientException.class);
    }

    @Test
    public void shouldUpdateCookie() {
        AtomicReference<String> usernameCookie = new AtomicReference<>("old user; with {}, \"quotations\" and '+' in name");
        String oldValue = controllerClient.changeUser(usernameCookie, "newUser");
        assertThat(oldValue).isEqualTo(oldValue);
        assertThat(usernameCookie.get()).isEqualTo("newUser");
    }

    @Test
    public void shouldHandleNullCookie() {
        assertThat(controllerClient.changeUser(null, "newUser")).isEqualTo("null");
    }

    @Test
    public void shouldHandleNewCookie() {
        AtomicReference<String> usernameCookie = new AtomicReference<>(null);
        String newUsername = "new user; with {}, \"quotations\" and '+' in name";
        assertThat(controllerClient.changeUser(usernameCookie, newUsername)).isEqualTo("null");
        assertThat(usernameCookie.get()).isEqualTo(newUsername);
    }

    @Test
    public void shouldReadAndWriteHeaders() {
        assertThat(controllerClient.downcase(Optional.of("VALUE"))).isEqualTo("value");
    }

    @Test
    public void shouldSendAndReceiveBytes() {
        byte[] input = {1, 2, 3, 4};
        assertThat(controllerClient.reverseBytes(input))
                .containsExactly(4, 3, 2, 1);
    }

    @Test
    public void shouldSetContentType() {
        AtomicReference<String> contentType = new AtomicReference<>();
        controllerClient.getImage(contentType::set);
        assertThat(contentType.get()).isEqualTo("image/png");
    }

    @Test
    public void shouldReceiveTextErrorMessage() {
        expectedLogEvents.setAllowUnexpectedLogs(true);
        assertThatThrownBy(() -> controllerClient.sendError("Something went wrong"))
                .isInstanceOf(HttpClientException.class)
                .satisfies(e -> assertThat(((HttpClientException) e).getResponseBody()).contains("MESSAGE: Something went wrong"));
    }

    @Test
    public void shouldReceiveHtmlErrorMessage() {
        expectedLogEvents.setAllowUnexpectedLogs(true);
        assertThatThrownBy(() -> controllerClient.sendHtmlError("It went wrong"))
                .isInstanceOf(HttpClientException.class)
                .satisfies(e -> assertThat(((HttpClientException) e).getResponseBody()).contains("<tr><th>MESSAGE:</th><td>It went wrong</td></tr>"));
    }

    @Test
    public void shouldRouteWithExtension() {
        assertThat(controllerClient.getHtmlFile("index"))
                .isEqualTo("<html><h2>Hello from index</h2></html>");
        assertThat(controllerClient.getTextFile("robots"))
                .isEqualTo("Hello from robots");
    }

    @Test
    public void shouldReadInputStream() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        controllerClient.getStream().transferTo(buffer);
        assertThat(buffer.toString()).isEqualTo("hello world");
    }

    @Test
    public void shouldReadReader() throws IOException {
        StringWriter buffer = new StringWriter();
        controllerClient.getReader().transferTo(buffer);
        assertThat(buffer.toString()).isEqualTo("Hello World");
    }

    @Test
    public void shouldGetNotModifiedWhenRereading() {
        AtomicReference<ZonedDateTime> lastUpdated = new AtomicReference<>();
        controllerClient.getStreamWithTimestamps(Optional.empty(), lastUpdated::set);
        assertThat(lastUpdated.get())
                .isEqualTo(ZonedDateTime.of(2020, 1, 12, 11, 12, 15, 0, ZoneId.systemDefault()));

        assertThatThrownBy(() -> controllerClient.getStreamWithTimestamps(Optional.of(lastUpdated.get()), lastUpdated::set))
                .isInstanceOf(HttpNotModifiedException.class);
    }

}

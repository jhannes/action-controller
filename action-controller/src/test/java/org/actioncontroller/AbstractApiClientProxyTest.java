package org.actioncontroller;

import org.actioncontroller.client.HttpClientException;
import org.actioncontroller.meta.ApiHttpExchange;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.extend.junit.ExpectedLogEventsRule;
import org.slf4j.event.Level;

import java.lang.annotation.RetentionPolicy;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings({"ConstantConditions", "OptionalAssignedToNull"})
public abstract class AbstractApiClientProxyTest {

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
                @UnencryptedCookie("sessionCookie") Consumer<String> sessionCookie
        ) {
            sessionCookie.accept(username + ":" + password);
        }

        @GET("/loginSession/me")
        @HttpResponseHeader("X-Username")
        public String whoAmI(@UnencryptedCookie("sessionCookie") Optional<String> sessionCookie) {
            return sessionCookie.map(s -> s.split(":")[0]).orElse("<none>");
        }

        @GET("/loginSession/me/required")
        @HttpResponseHeader("X-Username")
        public String whoAmIRequired(@UnencryptedCookie("sessionCookie") String sessionCookie) {
            return sessionCookie.split(":")[0];
        }

        @GET("/loginSession/endsession")
        @SendRedirect
        public String endsession(@UnencryptedCookie("sessionCookie") Consumer<String> setSessionCookie, @ContextUrl String url) {
            setSessionCookie.accept(null);
            return url + "/frontPage";
        }

        @GET("/explicitError")
        @ContentBody
        public String explicitError() {
            throw new HttpActionException(403, "You're not allowed to do this");
        }

        @GET("/lowercase")
        @HttpHeader("X-Result")
        public String downcase(@HttpHeader("X-Input") String value) {
            return value.toLowerCase();
        }

        @POST("/reverseBytes")
        @ContentBody
        public byte[] reverseBytes(@ContentBody byte[] bytes) {
            byte[] result = new byte[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                result[result.length-i-1] = bytes[i];
            }
            return result;
        }

        @GET("/image.png")
        @ContentBody
        public byte[] getImage(@HttpHeader("content-type") Consumer<String> setContentType) {
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
    }

    public static class UnmappedController {
        @GET("/not-mapped")
        @ContentBody
        public String notHere() {
            return "Not here";
        }
    }

    protected String baseUrl;

    protected TestController client;

    @Test
    public void shouldMakeSimpleHttpGet() {
        assertThat(client.first()).isEqualTo("Hello world");
    }

    @Test
    public void shouldRouteWithRequiredParameter() {
        String first = client.first("Hi!", null);
        assertThat(first).isEqualTo("Hi! /");
    }

    @Rule
    public ExpectedLogEventsRule expectedLogEvents = new ExpectedLogEventsRule(Level.WARN);

    @Test
    public void shouldReceiveParameters() {
        AtomicInteger value = new AtomicInteger();
        assertThat(client.upcase("Test string", value::set))
                .isEqualTo("Test string".toUpperCase());
        assertThat(value.get()).isEqualTo("Test string".length());
    }

    @Test
    public void shouldConvertParameters() {
        assertThat(client.divide(8, 2, false)).isEqualTo(4);
    }

    @Test
    public void shouldConvertEnums() {
        assertThat(client.enumText(Optional.of(RetentionPolicy.SOURCE))).isEqualTo("SOURCE");
    }

    @Test
    public void shouldOmitEmptyOptional() {
        assertThat(client.enumText(Optional.empty())).isEqualTo("<none>");
    }

    @Test
    public void shouldReportActionExceptions() {
        assertThatThrownBy(() -> client.explicitError())
                .isEqualTo(new HttpClientException(403, "Forbidden", "You're not allowed to do this", null))
                .satisfies(e -> assertThat(((HttpClientException) e).getResponseBody()).contains("You're not allowed to do this"))
                .satisfies(e -> assertThat(((HttpClientException) e).getUrl().toString()).endsWith("/explicitError"))
        ;
    }

    @Test
    public abstract void shouldRethrowRuntimeExceptions();

    @Test
    public void shouldHandleContentHeaders() {
        assertThat(client.storeNewEntry("someUrl")).endsWith("/entries/" + "someUrl");
    }

    @Test
    public void shouldHandleArgumentsInContentHeaders() {
        UUID id = UUID.randomUUID();
        assertThat(client.createNewResource(id)).isEqualTo(id);
    }

    @Test
    public void shouldHandlePathParams() {
        assertThat(client.getName("SomePerson")).isEqualTo("SomePerson's name");
    }

    private String sessionCookie;

    @Test
    public void shouldSetCookies() {
        client.putLoginSession("username", "let-me-in", s -> sessionCookie = s);
        assertThat(sessionCookie).isEqualTo("username:let-me-in");
    }

    @Test
    public void shouldReadCookies() {
        assertThat(client.whoAmI(Optional.of("someUser:let-me-in"))).isEqualTo("someUser");
    }

    @Test
    public void shouldEndSession() {
        client.putLoginSession("the user", "let-me-in", null);
        assertThat(client.whoAmI(null)).isEqualTo("the user");
        String redirectUrl = client.endsession(null, null);
        assertThat(redirectUrl).isEqualTo(baseUrl + "/frontPage");
        assertThat(client.whoAmI(null)).isEqualTo("<none>");
    }

    @Test
    public void shouldRequireCookie() {
        client.putLoginSession("the user", "let-me-in", null);
        assertThat(client.whoAmIRequired(null)).isEqualTo("the user");
        client.endsession(null, null);
        assertThatThrownBy(() -> client.whoAmIRequired(null))
            .isInstanceOf(HttpClientException.class);
    }

    @Test
    public void shouldReadAndWriteHeaders() {
        assertThat(client.downcase("VALUE")).isEqualTo("value");
    }

    @Test
    public void shouldSendAndReceiveBytes() {
        byte[] input = {1,2,3,4};
        assertThat(client.reverseBytes(input))
                .containsExactly(4, 3, 2, 1);
    }

    @Test
    public void shouldSetContentType() {
        AtomicReference<String> contentType = new AtomicReference<>();
        assertThat(client.getImage(contentType::set));
        assertThat(contentType.get()).isEqualTo("image/png");
    }

    @Test
    public void shouldReceiveTextErrorMessage() {
        expectedLogEvents.setAllowUnexpectedLogs(true);
        assertThatThrownBy(() -> client.sendError("Something went wrong"))
                .isInstanceOf(HttpClientException.class)
                .satisfies(e -> assertThat(((HttpClientException)e).getResponseBody()).contains("MESSAGE: Something went wrong"));
    }

    @Test
    public void shouldReceiveHtmlErrorMessage() {
        expectedLogEvents.setAllowUnexpectedLogs(true);
        assertThatThrownBy(() -> client.sendHtmlError("It went wrong"))
                .isInstanceOf(HttpClientException.class)
                .satisfies(e -> assertThat(((HttpClientException)e).getResponseBody()).contains("<tr><th>MESSAGE:</th><td>It went wrong</td></tr>"));
    }
}

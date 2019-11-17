package org.actioncontroller;

import org.actioncontroller.client.HttpClientException;
import org.actioncontroller.meta.ApiHttpExchange;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.extend.junit.ExpectedLogEventsRule;
import org.slf4j.event.Level;

import java.lang.annotation.RetentionPolicy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class AbstractApiClientProxyTest {

    public static class TestController {

        @Get("/")
        @ContentBody
        public String first() {
            return "Hello world";
        }

        @Get("/?greeting")
        @ContentBody
        public String first(@RequestParam("greeting") String greeting, ApiHttpExchange exchange) {
            return greeting + " " + exchange.getPathInfo();
        }

        @Post("/uppercase")
        @ContentBody
        public String upcase(
                @ContentBody String parameter,
                @HttpHeader("Content-characters") Consumer<Integer> setContentCharacters
        ) {
            setContentCharacters.accept(parameter.length());
            return parameter.toUpperCase();
        }

        @Get("/someNiceMath")
        @ContentBody
        public int divide(@RequestParam("whole") int whole, @RequestParam("divisor") int divisor) {
            return whole / divisor;
        }

        @Get("/enumText")
        @ContentBody
        public String enumText(@RequestParam("policy") Optional<RetentionPolicy> policy) {
            return policy.map(Enum::name).orElse("<none>");
        }

        @Post("/resource/:id")
        @ContentLocationHeader
        public String storeNewEntry(@PathParam("id") String id) {
            return "/entries/" + id;
        }

        @Put("/loginSession/me")
        public void putLoginSession(
                @RequestParam("username") String username,
                @RequestParam("password") String password,
                @UnencryptedCookie("sessionCookie") Consumer<String> sessionCookie
        ) {
            sessionCookie.accept(username + ":" + password);
        }

        @Get("/loginSession/me")
        @HttpResponseHeader("X-Username")
        public String whoAmI(@UnencryptedCookie("sessionCookie") Optional<String> sessionCookie) {
            return sessionCookie.map(s -> s.split(":")[0]).orElse("<none>");
        }

        @Get("/loginSession/endsession")
        @SendRedirect
        public String endsession(@UnencryptedCookie("sessionCookie") Consumer<String> setSessionCookie, @ContextUrl String url) {
            setSessionCookie.accept(null);
            return url + "/frontPage";
        }

        @Get("/explicitError")
        @ContentBody
        public String explicitError() {
            throw new HttpActionException(403, "You're not allowed to do this");
        }

        @Get("/lowercase")
        @HttpHeader("X-Result")
        public String downcase(@HttpHeader("X-Input") String value) {
            return value.toLowerCase();
        }
    }

    public static class UnmappedController {
        @Get("/not-mapped")
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
        assertThat(client.first("Hi!", null)).isEqualTo("Hi! /");
    }

    @Rule
    public ExpectedLogEventsRule expectedLogEvents = new ExpectedLogEventsRule(Level.WARN);

    @Test
    public void shouldReceiveParameters() {
        AtomicInteger value = new AtomicInteger();
        assertThat(client.upcase("Test string", newValue -> value.set(newValue)))
                .isEqualTo("Test string".toUpperCase());
        assertThat(value.get()).isEqualTo("Test string".length());
    }

    @Test
    public void shouldConvertParameters() {
        assertThat(client.divide(8, 2)).isEqualTo(4);
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
                .isEqualTo(new HttpClientException(403, "You're not allowed to do this"));
    }

    @Test
    public void shouldReportRuntimeExceptions() {
        expectedLogEvents.expect(
                ApiControllerAction.class,
                Level.ERROR,
                "While invoking TestController.divide(int,int)",
                new ArithmeticException("/ by zero")
        );
        assertThatThrownBy(() -> client.divide(10, 0))
                .isEqualTo(new HttpClientException(500, "Internal Server Error"));
    }

    @Test
    public void shouldHandleContentHeaders() {
        assertThat(client.storeNewEntry("someUrl")).endsWith("/entries/" + "someUrl");
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
    public void shouldReadAndWriteHeaders() {
        assertThat(client.downcase("VALUE")).isEqualTo("value");
    }

    // TODO: User in role

    // TODO: JsonBody (without JsonBuddy dependency)
}

package org.actioncontroller;

import org.actioncontroller.test.HttpClientException;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.extend.junit.ExpectedLogEventsRule;
import org.slf4j.event.Level;

import java.lang.annotation.RetentionPolicy;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class AbstractApiClientProxyTest {

    public static class TestController {

        @Get("/first")
        @ContentBody
        public String first() {
            return "Hello world";
        }

        @Get("/uppercase")
        @ContentBody
        public String upcase(@RequestParam("myParam") String parameter) {
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
            return sessionCookie.map(s -> s.split(":")[0]).orElse(null);
        }

        @Get("/explicitError")
        @ContentBody
        public String explicitError() {
            throw new HttpActionException(403, "You're not allowed to do this");
        }

    }

    public static class UnmappedController {
        @Get("/not-mapped")
        @ContentBody
        public String notHere() {
            return "Not here";
        }
    }

    protected TestController client;


    @Test
    public void shouldMakeSimpleHttpGet() {
        assertThat(client.first()).isEqualTo("Hello world");
    }

    @Rule
    public ExpectedLogEventsRule expectedLogEvents = new ExpectedLogEventsRule(Level.WARN);

    @Test
    public void shouldReceiveParameters() {
        assertThat(client.upcase("Test string")).isEqualTo("Test string".toUpperCase());
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
                .isEqualTo(new HttpClientException(403, "You're not allowed to do this", null));
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
                .isEqualTo(new HttpClientException(500, "Server Error", null));
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

    // TODO: User in role

    // TODO: Remove cookie

    // TODO: Sessions

    // TODO: HttpHeaders

    // TODO: JsonBody (without JsonBuddy dependency)
}
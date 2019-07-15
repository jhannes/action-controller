package org.actioncontroller;

import org.actioncontroller.test.ApiClientProxy;
import org.actioncontroller.test.HttpClientException;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.extend.junit.ExpectedLogEventsRule;
import org.slf4j.event.Level;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import java.lang.annotation.RetentionPolicy;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ApiClientProxyTest {

    private String baseUrl;

    public static class TestController {

        @Get("/first")
        @ContentBody
        public String first() {
            return "Hello world";
        }

        @Get("/upcase")
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
            return sessionCookie.get().split(":")[0];
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

    private TestController client;

    @Before
    public void createServerAndClient() throws Exception {
        Server server = new Server(0);
        ServletContextHandler handler = new ServletContextHandler();
        handler.addEventListener(new javax.servlet.ServletContextListener() {
            @Override
            public void contextInitialized(ServletContextEvent event) {
                event.getServletContext().addServlet("testApi", new ApiServlet() {
                    @Override
                    public void init() throws ServletException {
                        registerController(new TestController());
                    }
                }).addMapping("/api/*");
            }
        });
        handler.setContextPath("/test");
        server.setHandler(handler);
        server.start();

        baseUrl = server.getURI() + "/api";
        client = ApiClientProxy.create(TestController.class, baseUrl);
    }

    @Test
    public void shouldMakeSimpleHttpGet() {
        assertThat(client.first()).isEqualTo("Hello world");
    }

    @Rule
    public ExpectedLogEventsRule expectedLogEvents = new ExpectedLogEventsRule(Level.WARN);

    @Test
    public void gives404OnUnmappedController() {
        UnmappedController unmappedController = ApiClientProxy.create(UnmappedController.class, baseUrl);
        assertThatThrownBy(unmappedController::notHere)
                .isInstanceOf(HttpActionException.class)
                .satisfies(e -> {
                    assertThat(((HttpActionException)e).getStatusCode()).isEqualTo(404);
                });
        expectedLogEvents.expect(ApiServlet.class, Level.WARN, "No route for GET /test/api[/not-mapped]");
    }

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
        assertThatThrownBy(() -> client.divide(10, 0))
                .isEqualTo(new HttpClientException(500, "Server Error", null));
        expectedLogEvents.expect(
                ApiControllerAction.class,
                Level.ERROR,
                "While invoking TestController.divide(int,int)",
                new ArithmeticException("/ by zero")
        );
    }

    @Test
    public void shouldHandleContentHeaders() {
        assertThat(client.storeNewEntry("someUrl")).isEqualTo(baseUrl + "/entries/" + "someUrl");
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

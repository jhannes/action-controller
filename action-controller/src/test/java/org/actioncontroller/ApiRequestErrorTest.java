package org.actioncontroller;

import org.actioncontroller.servlet.ApiServlet;
import org.fakeservlet.FakeServletContainer;
import org.fakeservlet.FakeServletRequest;
import org.fakeservlet.FakeServletResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.extend.junit.ExpectedLogEventsRule;
import org.slf4j.event.Level;

import javax.servlet.ServletException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class ApiRequestErrorTest {

    private URL contextRoot;

    public static class Controller {
        @GET("/hello")
        @ContentBody
        public String sayHello(@RequestParam("number") long number) {
            return String.valueOf(number);
        }

        @GET("/redirect")
        @SendRedirect
        public Object redirect() {
            return null;
        }
        
        @GET("/round")
        @ContentBody
        public BigDecimal round(@RequestParam("mode") RoundingMode mode, @RequestParam("value") double value) {
            return new BigDecimal(value).setScale(2, mode);
        }
    }

    private ApiServlet servlet = new ApiServlet(new Controller());
    private final FakeServletContainer container = new FakeServletContainer("http://my.example.com:8080/my/context", "/actions");

    @Before
    public void setup() throws ServletException, MalformedURLException {
        servlet.init(null);
        contextRoot = new URL("http://my.example.com:8080/my/context");
    }

    @Rule
    public ExpectedLogEventsRule expectedLogEvents = new ExpectedLogEventsRule(Level.WARN);

    @Test
    public void shouldReportErrorOnUnmappedRootAction() throws IOException, ServletException {
        FakeServletRequest request = container.newRequest("GET", null);

        expectedLogEvents.expectMatch(e -> e
                .logger(ApiControllerRouteMap.class)
                .pattern("No route for {}. Routes {}"));
        FakeServletResponse resp = request.service(servlet);

        assertThat(resp.getStatus()).isEqualTo(404);
    }

    @Test
    public void shouldReportErrorOnParameterMismatch() throws IOException, ServletException {
        FakeServletRequest request = container.newRequest("GET", "/hello");
        request.addParameter("number", "hello");

        FakeServletResponse resp = request.service(servlet);

        assertThat(resp.getStatus()).isEqualTo(400);
    }

    @Test
    public void shouldReportErrorOnReturnValueMismatch() throws ServletException, IOException {
        FakeServletRequest request = container.newRequest("GET", "/redirect");
        expectedLogEvents.expectMatch(e -> e
                .level(Level.ERROR)
                .logger(ApiControllerAction.class)
                .pattern("While converting {} return value {}")
                .exception(NullPointerException.class));
        FakeServletResponse resp = request.service(servlet);
        assertThat(resp.getStatus()).isEqualTo(500);
    }
    
    @Test
    public void shouldReportErrorOnWrongEnumValue() throws ServletException, IOException {
        FakeServletRequest request = container.newRequest("GET", "/round");
        request.addParameter("mode", "invalid");
        request.addParameter("value", "2.55");

        FakeServletResponse resp = request.service(servlet);
        assertThat(resp.getStatus()).isEqualTo(400);
        assertThat(resp.getStatusMessage()).isEqualTo("Value 'invalid' not in [UP, DOWN, CEILING, FLOOR, HALF_UP, HALF_DOWN, HALF_EVEN, UNNECESSARY]");
    }
}

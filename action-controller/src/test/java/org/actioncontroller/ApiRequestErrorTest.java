package org.actioncontroller;

import org.actioncontroller.servlet.ApiServlet;
import org.fakeservlet.FakeServletRequest;
import org.fakeservlet.FakeServletResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.extend.junit.ExpectedLogEventsRule;
import org.slf4j.event.Level;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class ApiRequestErrorTest {

    private FakeServletResponse resp = new FakeServletResponse();
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
    }

    private ApiServlet servlet = new ApiServlet(new Controller());

    @Before
    public void setup() throws ServletException, MalformedURLException {
        servlet.init(null);
        contextRoot = new URL("http://my.example.com:8080/my/context");
    }

    @Rule
    public ExpectedLogEventsRule expectedLogEvents = new ExpectedLogEventsRule(Level.WARN);

    @Test
    public void shouldReportErrorOnUnmappedRootAction() throws IOException, ServletException {
        FakeServletRequest request = new FakeServletRequest("GET", contextRoot, "/actions", null);

        expectedLogEvents.expectMatch(e -> e
                .logger(ApiServlet.class)
                .formattedMessage("No route for GET /my/context/actions[]"));
        servlet.service(request, resp);

        assertThat(resp.getStatus()).isEqualTo(404);
    }

    @Test
    public void shouldReportErrorOnParameterMismatch() throws IOException, ServletException {
        FakeServletRequest request = new FakeServletRequest("GET", contextRoot, "/actions", "/hello");
        request.setParameter("number", "hello");

        servlet.service(request, resp);

        assertThat(resp.getStatus()).isEqualTo(400);
    }

    @Test
    public void shouldReportErrorOnReturnValueMismatch() throws ServletException, IOException {
        FakeServletRequest request = new FakeServletRequest("GET", contextRoot, "/actions", "/redirect");
        expectedLogEvents.expectMatch(e -> e
                .level(Level.ERROR)
                .logger(ApiControllerAction.class)
                .pattern("While converting {} return value {}")
                .exception(NullPointerException.class));
        servlet.service(request, resp);

        assertThat(resp.getStatus()).isEqualTo(500);
    }
}

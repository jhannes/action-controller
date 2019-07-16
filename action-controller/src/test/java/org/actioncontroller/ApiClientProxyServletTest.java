package org.actioncontroller;

import org.actioncontroller.servlet.ApiServlet;
import org.actioncontroller.test.ApiClientProxy;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.event.Level;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ApiClientProxyServletTest extends AbstractApiClientProxyTest {

    private String baseUrl;

    @Before
    public void createServerAndClient() throws Exception {
        Server server = new Server(0);
        ServletContextHandler handler = new ServletContextHandler();
        handler.setSessionHandler(new SessionHandler());
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
    public void gives404OnUnmappedController() {
        expectedLogEvents.expect(ApiServlet.class, Level.WARN, "No route for GET /test/api[/not-mapped]");
        UnmappedController unmappedController = ApiClientProxy.create(UnmappedController.class, baseUrl);
        assertThatThrownBy(unmappedController::notHere)
                .isInstanceOf(HttpActionException.class)
                .satisfies(e -> assertThat(((HttpActionException)e).getStatusCode()).isEqualTo(404));
    }

}

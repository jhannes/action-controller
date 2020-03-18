package org.actioncontroller;

import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.client.HttpURLConnectionApiClient;
import org.actioncontroller.servlet.ApiServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.Before;

import javax.servlet.ServletContextEvent;

public class ServletHttpPrincipalTest extends AbstractHttpPrincipalTest {

    @Before
    public void createServerAndClient() throws Exception {
        Server server = new Server(0);
        ServletContextHandler handler = new ServletContextHandler();
        handler.addEventListener(new javax.servlet.ServletContextListener() {
            @Override
            public void contextInitialized(ServletContextEvent event) {
                event.getServletContext()
                        .addServlet("testApi", new ApiServlet(new AuthenticatedController()))
                        .addMapping("/api/*");
            }
        });
        handler.setContextPath("/test");
        server.setHandler(handler);

        server.start();
        HttpURLConnectionApiClient apiClient = new HttpURLConnectionApiClient(server.getURI() + "/api");
        client = ApiClientClassProxy.create(AuthenticatedController.class, apiClient);
    }
}

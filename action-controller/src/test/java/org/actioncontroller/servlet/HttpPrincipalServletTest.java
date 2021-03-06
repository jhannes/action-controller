package org.actioncontroller.servlet;

import org.actioncontroller.AbstractHttpPrincipalTest;
import org.actioncontroller.client.ApiClient;
import org.actioncontroller.client.HttpURLConnectionApiClient;
import org.actioncontroller.servlet.ApiServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;

import javax.servlet.ServletContextEvent;

public class HttpPrincipalServletTest extends AbstractHttpPrincipalTest {

    @Override
    protected ApiClient createApiClient(Object controller) throws Exception {
        Server server = new Server(0);
        ServletContextHandler handler = new ServletContextHandler();
        handler.addEventListener(new javax.servlet.ServletContextListener() {
            @Override
            public void contextInitialized(ServletContextEvent event) {
                event.getServletContext()
                        .addServlet("testApi", new ApiServlet(controller))
                        .addMapping("/api/*");
            }
        });
        handler.setContextPath("/test");
        server.setHandler(handler);

        server.start();
        return new HttpURLConnectionApiClient(server.getURI() + "/api");
    }
}

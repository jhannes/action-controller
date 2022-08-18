package org.actioncontroller.values.json;

import org.actioncontroller.client.ApiClient;
import org.actioncontroller.client.HttpURLConnectionApiClient;
import org.actioncontroller.servlet.ApiServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;

import javax.servlet.ServletContextEvent;

public class UnencryptedJsonCookieServletTest extends UnencryptedJsonCookieTest {
    @Override
    protected ApiClient createClient(Controller controller) throws Exception {
        Server server = new Server();
        ServletContextHandler handler = new ServletContextHandler();
        handler.setSessionHandler(new SessionHandler());
        handler.addEventListener(new javax.servlet.ServletContextListener() {
            @Override
            public void contextInitialized(ServletContextEvent event) {
                event.getServletContext().addServlet("testApi", new ApiServlet(controller)).addMapping("/api/*");
            }
        });
        handler.setContextPath("/test");
        server.setHandler(handler);
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(0);
        connector.setHost("localhost");
        server.addConnector(connector);
        server.start();

        return new HttpURLConnectionApiClient(server.getURI().toString() + "/api");
    }

}

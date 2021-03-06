package org.actioncontroller.servlet;

import org.actioncontroller.AbstractApiClientSessionTest;
import org.actioncontroller.client.HttpURLConnectionApiClient;
import org.eclipse.jetty.security.DefaultUserIdentity;
import org.eclipse.jetty.security.UserAuthentication;
import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;

import javax.security.auth.Subject;
import javax.servlet.DispatcherType;
import javax.servlet.ServletContextEvent;
import java.security.Principal;
import java.util.EnumSet;

public class ApiServletSessionTest extends AbstractApiClientSessionTest {

    private Authentication authentication;

    @Override
    protected HttpURLConnectionApiClient createClient(Object controller) throws Exception {
        Server server = new Server(0);
        ServletContextHandler handler = new ServletContextHandler();
        handler.setSessionHandler(new SessionHandler());
        handler.addEventListener(new javax.servlet.ServletContextListener() {
            @Override
            public void contextInitialized(ServletContextEvent event) {
                event.getServletContext().addFilter("user", (request, response, chain) -> {
                    ((Request)request).setAuthentication(authentication);
                    chain.doFilter(request, response);
                }).addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
                event.getServletContext().addServlet("testApi", new ApiServlet(controller)).addMapping("/api/*");
            }
        });
        handler.setContextPath("/test");
        server.setHandler(handler);
        server.start();

        baseUrl = server.getURI() + "/api";
        return new HttpURLConnectionApiClient(baseUrl);
    }

    @Override
    public void doAuthenticate(Principal principal) {
        this.authentication = createAuthentication(principal);
    }

    protected UserAuthentication createAuthentication(Principal principal) {
        Subject subject = new Subject();
        subject.getPrincipals().add(principal);
        UserIdentity userIdentity = new DefaultUserIdentity(subject, principal, new String[0]);
        return new UserAuthentication("test", userIdentity);
    }

}

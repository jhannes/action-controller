package org.actioncontroller;

import org.actioncontroller.client.HttpURLConnectionApiClient;
import org.actioncontroller.servlet.ApiServlet;
import org.actioncontroller.client.ApiClientProxy;
import org.actioncontroller.client.HttpClientException;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.extend.junit.ExpectedLogEventsRule;
import org.slf4j.event.Level;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AbstractApiClientSessionTest {

    protected LoginController client;
    protected String baseUrl;

    public static class LoginSession {
        List<String> favorites = new ArrayList<>();
    }

    public static class LoginController {

        @Post("/favorites")
        public void addFavorite(
                @RequestParam("favorite") String favorite,
                @SessionParameter(createIfMissing = true) LoginSession loginSession
        ) {
            loginSession.favorites.add(favorite);
        }

        @Get("/favorites")
        @ContentBody
        public String getFavorites(@SessionParameter Optional<LoginSession> session) {
            return session.map(s -> String.join(", ", s.favorites))
                    .orElse("<no session>");
        }

        @Post("/login")
        @SendRedirect
        public String login(
                @RequestParam("username") String username,
                @SessionParameter(value = "username", createIfMissing = true, changeSessionId = true) Consumer<String> sessionUsername
        ) {
            sessionUsername.accept(username);
            return "userinfo";
        }

        @Get("/userinfo")
        @ContentBody
        public String userinfo(@SessionParameter("username") String username) {
            return username;
        }

        @Post("/logout")
        @SendRedirect
        public String logout(@SessionParameter(invalidate = true) Consumer<String> username) {
            username.accept(null);
            return "favorites";
        }
    }

    @Rule
    public ExpectedLogEventsRule expectedLogEvents = new ExpectedLogEventsRule(Level.WARN);

    @Before
    public void createServerAndClient() throws Exception {
        Server server = new Server(0);
        ServletContextHandler handler = new ServletContextHandler();
        handler.setSessionHandler(new SessionHandler());
        handler.addEventListener(new javax.servlet.ServletContextListener() {
            @Override
            public void contextInitialized(ServletContextEvent event) {
                event.getServletContext().addServlet("testApi", new ApiServlet(new LoginController())).addMapping("/api/*");
            }
        });
        handler.setContextPath("/test");
        server.setHandler(handler);
        server.start();

        baseUrl = server.getURI() + "/api";
        client = ApiClientProxy.create(LoginController.class, new HttpURLConnectionApiClient(baseUrl));
    }

    @Test
    public void shouldUseSession() {
        client.addFavorite("first", null);
        client.addFavorite("second", null);
        assertThat(client.getFavorites(null)).isEqualTo("first, second");
    }

    @Test
    public void shouldLogin() {
        String redirectUri = client.login("alice", null);
        assertThat(redirectUri).isEqualTo(baseUrl + "/userinfo");
        assertThat(client.userinfo(null)).isEqualTo("alice");
    }

    @Test
    public void shouldLogout() {
        client.login("alice", null);
        client.addFavorite("first", null);
        String logoutRedirect = client.logout(null);
        assertThat(logoutRedirect).isEqualTo(baseUrl + "/favorites");
        assertThat(client.getFavorites(null)).isEqualTo("<no session>");

        expectedLogEvents.expectPattern(ApiControllerAction.class, Level.WARN, "While processing {} arguments");
        assertThatThrownBy(() -> client.userinfo(null))
                .isEqualTo(new HttpClientException(401, "Missing required session parameter username"));
    }

}

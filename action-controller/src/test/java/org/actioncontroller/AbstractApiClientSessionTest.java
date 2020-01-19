package org.actioncontroller;

import org.actioncontroller.client.HttpURLConnectionApiClient;
import org.actioncontroller.servlet.ApiServlet;
import org.actioncontroller.client.ApiClientProxy;
import org.actioncontroller.client.HttpClientException;
import org.eclipse.jetty.security.DefaultUserIdentity;
import org.eclipse.jetty.security.UserAuthentication;
import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.extend.junit.ExpectedLogEventsRule;
import org.slf4j.event.Level;

import javax.security.auth.Subject;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AbstractApiClientSessionTest {

    protected LoginController client;
    protected String baseUrl;
    private Authentication authentication;

    public static class LoginSession {
        List<String> favorites = new ArrayList<>();
    }

    public static class TestPrincipal implements Principal {
        private final String name;
        private final boolean isAdmin;

        public TestPrincipal(String name, boolean isAdmin) {
            this.name = name;
            this.isAdmin = isAdmin;
        }

        @Override
        public String getName() {
            return name;
        }

        public boolean isAdmin() {
            return isAdmin;
        }
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
                @SessionParameter(value = "username", createIfMissing = true) Consumer<String> sessionUsername
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

        @Get("/remoteUser")
        @ContentBody
        public String remoteUser(@RequestParam.RemoteUser String remoteUser) {
            return remoteUser;
        }

        @Get("/remoteUser/optional")
        @ContentBody
        public String optionalUser(@RequestParam.RemoteUser Optional<String> remoteUser) {
            return remoteUser.orElse("<not logged in>");
        }

        @Get("/principal")
        @ContentBody
        public String remotePrincipal(@RequestParam.Principal TestPrincipal principal) {
            return principal.getName() + " admin=" + principal.isAdmin();
        }

        @Get("/principal/optional")
        @ContentBody
        public String optionalPrincipal(@RequestParam.Principal Optional<TestPrincipal> principal) {
            return principal.map(p -> p.getName() + " admin=" + p.isAdmin()).orElse("<none>");
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
                event.getServletContext().addFilter("user", new Filter() {
                    @Override
                    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
                        ((Request)request).setAuthentication(authentication);
                        chain.doFilter(request, response);
                    }
                }).addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
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

        expectedLogEvents.expectPattern(ApiControllerAction.class, Level.WARN, "While processing {} arguments to {}");
        assertThatThrownBy(() -> client.userinfo(null))
                .isEqualTo(new HttpClientException(401, "Missing required session parameter username"));
    }

    @Test
    public void shouldGetRemoteUsername() {
        Principal principal = () -> "Test Name";
        Subject subject = new Subject();
        subject.getPrincipals().add(principal);
        UserIdentity userIdentity = new DefaultUserIdentity(subject, principal, new String[0]);
        authentication = new UserAuthentication("test", userIdentity);

        assertThat(client.remoteUser("Test Name")).isEqualTo("Test Name");
    }

    @Test
    public void shouldGetClientPrincipal() {
        Subject subject = new Subject();
        TestPrincipal testPrincipal = new TestPrincipal("Other Name", true);
        subject.getPrincipals().add(testPrincipal);
        UserIdentity userIdentity = new DefaultUserIdentity(subject, testPrincipal, new String[0]);
        authentication = new UserAuthentication("test", userIdentity);

        assertThat(client.remotePrincipal(testPrincipal)).isEqualTo("Other Name admin=true");
    }

    @Test
    public void shouldAllowMissingPrincipal() {
        assertThat(client.optionalPrincipal(Optional.empty())).isEqualTo("<none>");
    }

    @Test
    public void shouldAllowMissingUser() {
        assertThat(client.optionalUser(Optional.empty())).isEqualTo("<not logged in>");
    }

}

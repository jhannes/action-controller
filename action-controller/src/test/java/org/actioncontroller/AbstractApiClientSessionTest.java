package org.actioncontroller;

import org.actioncontroller.actions.GET;
import org.actioncontroller.actions.POST;
import org.actioncontroller.client.ApiClient;
import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.client.HttpClientException;
import org.actioncontroller.values.ContentBody;
import org.actioncontroller.values.RequestParam;
import org.actioncontroller.values.SendRedirect;
import org.actioncontroller.values.SessionParameter;
import org.actioncontroller.values.UserPrincipal;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.extend.junit.ExpectedLogEventsRule;
import org.slf4j.event.Level;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("OptionalAssignedToNull")
public abstract class AbstractApiClientSessionTest {

    protected LoginController controllerClient;
    protected String baseUrl;

    public static class LoginSession {
        List<String> favorites = new ArrayList<>();
    }

    public static class TestPrincipal implements Principal {
        private final String name;

        public TestPrincipal(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        public boolean isAdmin() {
            return false;
        }
    }

    public static class AdminPrincipal extends TestPrincipal {

        public AdminPrincipal(String name) {
            super(name);
        }

        @Override
        public boolean isAdmin() {
            return true;
        }
    }

    public static class LoginController {

        @POST("/favorites")
        public void addFavorite(
                @RequestParam("favorite") String favorite,
                @SessionParameter(createIfMissing = true) LoginSession loginSession
        ) {
            loginSession.favorites.add(favorite);
        }

        @GET("/favorites")
        @ContentBody
        public String getFavorites(@SessionParameter Optional<LoginSession> session) {
            return session.map(s -> String.join(", ", s.favorites))
                    .orElse("<no session>");
        }

        @POST("/login")
        @SendRedirect
        public String login(
                @RequestParam("username") String username,
                @SessionParameter(value = "username", createIfMissing = true) Consumer<String> sessionUsername
        ) {
            sessionUsername.accept(username);
            return "userinfo";
        }

        @GET("/userinfo")
        @ContentBody
        public String userinfo(@SessionParameter("username") String username) {
            return username;
        }

        @POST("/logout")
        @SendRedirect
        public String logout(@SessionParameter(invalidate = true) Consumer<String> username) {
            username.accept(null);
            return "favorites";
        }

        @GET("/remoteUser")
        @ContentBody
        public String remoteUser(@RequestParam.RemoteUser String remoteUser) {
            return remoteUser;
        }

        @GET("/remoteUser/optional")
        @ContentBody
        public String optionalUser(@RequestParam.RemoteUser Optional<String> remoteUser) {
            return remoteUser.orElse("<not logged in>");
        }

        @GET("/principal/optional")
        @ContentBody
        public String optionalPrincipal(@UserPrincipal Optional<TestPrincipal> principal) {
            return principal.map(p -> p.getName() + " admin=" + p.isAdmin()).orElse("<none>");
        }

        @GET("/admin/optional")
        @ContentBody
        public String optionalAdmin(@UserPrincipal Optional<AdminPrincipal> principal) {
            return principal.map(TestPrincipal::getName).orElse("<none>");
        }

        @GET("/admin/required")
        @ContentBody
        public String requiredAdmin(@UserPrincipal AdminPrincipal principal) {
            return principal.getName();
        }
    }

    @Rule
    public ExpectedLogEventsRule expectedLogEvents = new ExpectedLogEventsRule(Level.WARN);

    @Before
    public void createServerAndClient() throws Exception {
        ApiClient apiClient = createClient(new LoginController());
        this.controllerClient = ApiClientClassProxy.create(LoginController.class, apiClient);
    }

    protected abstract ApiClient createClient(Object controller) throws Exception;

    public abstract void doAuthenticate(Principal principal);
    
    @Test
    public void shouldUseSession() {
        controllerClient.addFavorite("first", null);
        controllerClient.addFavorite("second", null);
        assertThat(controllerClient.getFavorites(null)).isEqualTo("first, second");
    }

    @Test
    public void shouldLogin() {
        String redirectUri = controllerClient.login("alice", null);
        assertThat(redirectUri).isEqualTo(baseUrl + "/userinfo");
        assertThat(controllerClient.userinfo(null)).isEqualTo("alice");
    }

    @Test
    public void shouldLogout() {
        controllerClient.login("alice", null);
        controllerClient.addFavorite("first", null);
        String logoutRedirect = controllerClient.logout(null);
        assertThat(logoutRedirect).isEqualTo(baseUrl + "/favorites");
        assertThat(controllerClient.getFavorites(null)).isEqualTo("<no session>");

        assertThatThrownBy(() -> controllerClient.userinfo(null))
                .isEqualTo(new HttpClientException(401, "Unauthorized", "Missing required session parameter username", null));
    }

    @Test
    public void shouldAllowMissingUser() {
        assertThat(controllerClient.optionalUser(null)).isEqualTo("<not logged in>");
    }

    @Test
    public void shouldAllowMissingPrincipal() {
        assertThat(controllerClient.optionalAdmin(null)).isEqualTo("<none>");
    }

    @Test
    public void shouldGetRemoteUsername() {
        doAuthenticate(() -> "Test Name");
        assertThat(controllerClient.remoteUser(null)).isEqualTo("Test Name");
    }

    @Test
    public void shouldAllowAuthorizedOptionalUser() {
        doAuthenticate(new AdminPrincipal("Admin"));
        assertThat(controllerClient.optionalAdmin(null)).isEqualTo("Admin");
    }

    @Test
    public void shouldAllowUnauthorizedOptionalUser() {
        doAuthenticate(new TestPrincipal("Other Name"));
        assertThat(controllerClient.optionalAdmin(null)).isEqualTo("<none>");
    }

    @Test
    public void shouldRejectUnauthorizedUser() {
        doAuthenticate(new TestPrincipal("Other Name"));
        assertThatThrownBy(() -> controllerClient.requiredAdmin(null))
                .isInstanceOf(HttpClientException.class)
                .extracting("statusCode")
                .isEqualTo(403);
    }

}

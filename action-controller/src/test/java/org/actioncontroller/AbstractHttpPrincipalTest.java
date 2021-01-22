package org.actioncontroller;

import org.actioncontroller.client.ApiClient;
import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.client.HttpClientException;
import org.junit.Before;
import org.junit.Test;

import java.security.Principal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class AbstractHttpPrincipalTest {

    public static class UserPrincipal implements Principal {
        private final String username;

        public UserPrincipal(String username) {
            this.username = username;
        }

        @Override
        public String getName() {
            return username;
        }
    }

    public static class AdminPrincipal extends UserPrincipal {

        public AdminPrincipal(String username) {
            super(username);
        }
    }

    public static class AuthenticatedController {

        @GET("/user/optional")
        @ContentBody
        public String optionalUser(@org.actioncontroller.UserPrincipal Optional<UserPrincipal> principal) {
            return principal.map(Principal::getName).orElse("<none>");
        }

        @GET("/user/required")
        public void requiredUser(@org.actioncontroller.UserPrincipal UserPrincipal principal) {

        }

        @GET("/admin/optional")
        public void optionalAdmin(@org.actioncontroller.UserPrincipal Optional<AdminPrincipal> principal) {

        }
    }

    protected AuthenticatedController clientController;

    @Before
    public void createServerAndClient() throws Exception {
        clientController = ApiClientClassProxy.create(
                AuthenticatedController.class,
                createApiClient(new AuthenticatedController())
        );
    }

    protected abstract ApiClient createApiClient(Object controller) throws Exception;

    @Test
    public void shouldAcceptNoUserWhenPrincipalIsOptional() {
        clientController.optionalUser(Optional.empty());
    }

    @Test
    public void shouldAcceptAdminPrincipal() {
        clientController.optionalAdmin(Optional.of(new AdminPrincipal("admin")));
    }

    @Test
    public void shouldRejectUnauthenticatedUser() {
        assertThatThrownBy(() -> clientController.requiredUser(null))
                .isInstanceOf(HttpClientException.class)
                .extracting("statusCode")
                .isEqualTo(401);
    }

}

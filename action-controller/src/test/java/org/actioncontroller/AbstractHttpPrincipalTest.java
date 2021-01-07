package org.actioncontroller;

import org.actioncontroller.client.HttpClientException;
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

    protected AuthenticatedController client;

    @Test
    public void shouldAcceptNoUserWhenPrincipalIsOptional() {
        client.optionalUser(Optional.empty());
    }

    @Test
    public void shouldAcceptAdminPrincipal() {
        client.optionalAdmin(Optional.of(new AdminPrincipal("admin")));
    }

    @Test
    public void shouldRejectUnauthenticatedUser() {
        assertThatThrownBy(() -> client.requiredUser(null))
                .isInstanceOf(HttpClientException.class)
                .extracting("statusCode")
                .isEqualTo(401);
    }

}

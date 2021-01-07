package org.actioncontrollerdemo;

import org.actioncontroller.ContentBody;
import org.actioncontroller.GET;
import org.actioncontroller.POST;
import org.actioncontroller.RequestParam;
import org.actioncontroller.SendRedirect;
import org.actioncontroller.UnencryptedCookie;
import org.actioncontroller.UserPrincipal;

import java.security.Principal;
import java.util.Optional;
import java.util.function.Consumer;

public class UserController {

    @GET("/user/optional")
    @ContentBody
    public String getUsername(
            @UserPrincipal Optional<Principal> principal
    ) {
        return "Hello, " + principal.map(Principal::getName).orElse("stranger");
    }

    @GET("/user/required")
    @ContentBody
    public String getRealUsername(
            @UserPrincipal Principal principal
    ) {
        return "Hello - required, " + principal.getName();
    }

    @GET("/user/admin")
    @ContentBody
    public String getAdminPage(
            @UserPrincipal AdminPrincipal principal
    ) {
        return "Hello - boss, " + principal.getName();
    }

    @GET("/login")
    @ContentBody(contentType = "text/html")
    public String getLogin(@RequestParam("redirectAfterLogin") Optional<String> redirectAfterLogin) {
        return "<form method='post'>" +
                "<input name='username' />" +
                redirectAfterLogin.map(url -> "<input type='hidden' name='redirectAfterLogin' value='" + url + "' />").orElse("") +
                "<button>Log in</button>" +
                "</form>";
    }

    @POST("/login")
    @SendRedirect
    public String postLogin(
            @RequestParam("username") String username,
            @RequestParam("redirectAfterLogin") Optional<String> redirectAfterLogin,
            @UnencryptedCookie(value = "username", secure = false) Consumer<String> setUsername
    ) {
        setUsername.accept(username);
        return redirectAfterLogin.orElse("user/required");
    }

}

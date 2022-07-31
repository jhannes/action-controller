package org.actioncontrollerdemo;

import org.actioncontroller.values.ContentBody;
import org.actioncontroller.actions.GET;
import org.actioncontroller.actions.POST;
import org.actioncontroller.values.RequestParam;
import org.actioncontroller.values.SendRedirect;
import org.actioncontroller.values.UnencryptedCookiePreview;
import org.actioncontroller.values.UserPrincipal;

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
            @UnencryptedCookiePreview(value = "username", secure = false) Consumer<String> setUsername
    ) {
        setUsername.accept(username);
        return redirectAfterLogin.orElse("user/required");
    }

}

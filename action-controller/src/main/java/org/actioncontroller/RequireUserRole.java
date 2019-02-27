package org.actioncontroller;

import org.actioncontroller.json.JsonHttpRequestException;
import org.actioncontroller.meta.ActionControllerFilter;
import org.actioncontroller.meta.FilterHandler;
import org.jsonbuddy.JsonObject;

import javax.servlet.http.HttpServletRequest;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@ActionControllerFilter(RequireUserRole.Handler.class)
public @interface RequireUserRole {

    String value();

    public class Handler implements FilterHandler {

        private RequireUserRole annotation;

        Handler(RequireUserRole annotation) {
            this.annotation = annotation;
        }


        public void precondition(
                HttpServletRequest request,
                Method action
        ) {
            String role = annotation.value();
            if (request.getRemoteUser() == null) {
                throw new JsonHttpRequestException(401,
                        "User must be logged in for " + action,
                        new JsonObject().put("message", "Login required"));
            }
            if (request.isUserInRole(role)) {
                throw new JsonHttpRequestException(403,
                        "User failed to authenticate for " + action + ": Missing role " + role + " for user",
                        new JsonObject().put("message", "Insufficient permissions"));
            }
        }


    }
}

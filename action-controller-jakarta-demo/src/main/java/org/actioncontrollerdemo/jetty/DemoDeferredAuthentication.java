package org.actioncontrollerdemo.jetty;

import org.actioncontroller.jakarta.JakartaServletHttpExchange;
import org.actioncontroller.servlet.ServletHttpExchange;
import org.actioncontrollerdemo.DemoPrincipal;
import org.eclipse.jetty.security.DefaultUserIdentity;
import org.eclipse.jetty.security.UserAuthentication;
import org.eclipse.jetty.server.Authentication;

import javax.security.auth.Subject;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

public class DemoDeferredAuthentication implements Authentication.Deferred {

    @Override
    public Authentication authenticate(ServletRequest servletRequest) {
        return JakartaServletHttpExchange.getCookie("username", (HttpServletRequest) servletRequest)
                .stream()
                .map(s -> getAuthentication(DemoPrincipal.createPrincipal(s)))
                .findFirst()
                .orElse(this);
    }

    private Authentication getAuthentication(Principal principal) {
        Subject subject = new Subject();
        subject.getPrincipals().add(principal);
        return new UserAuthentication("demo", new DefaultUserIdentity(subject, principal, new String[0]));
    }

    @Override
    public Authentication authenticate(ServletRequest servletRequest, ServletResponse servletResponse) {
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        try {
            String redirectRequest = req.getRequestURI();
            if (req.getQueryString() != null) {
                redirectRequest += "?" + req.getQueryString();
            }

            ((HttpServletResponse)servletResponse).sendRedirect("/demo/api/login?redirectAfterLogin="
                + URLEncoder.encode(redirectRequest, StandardCharsets.UTF_8));
            return Authentication.SEND_CONTINUE;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Authentication login(String s, Object o, ServletRequest servletRequest) {
        return null;
    }

    @Override
    public Authentication logout(ServletRequest servletRequest) {
        return null;
    }
}

package org.actioncontrollerdemo.servlet;

import org.actioncontrollerdemo.AdminPrincipal;
import org.actioncontrollerdemo.DemoPrincipal;
import org.actioncontrollerdemo.DemoUser;
import org.eclipse.jetty.security.DefaultUserIdentity;
import org.eclipse.jetty.security.UserAuthentication;
import org.eclipse.jetty.server.Authentication;

import javax.security.auth.Subject;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;

public class DemoDeferredAuthentication implements Authentication.Deferred {
    @Override
    public Authentication authenticate(ServletRequest servletRequest) {
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        return Optional.ofNullable(req.getCookies())
                .flatMap(cookies -> Stream.of(cookies).filter(c -> c.getName().equals("username")).findAny())
                .map(c -> fetchUserInfo(c.getValue()))
                .map(this::createAuthentication)
                .orElse(this);
    }

    private Authentication createAuthentication(DemoUser demoUser) {
        Subject subject = new Subject();
        DemoPrincipal principal = demoUser.getUsername().equals("johannes")
                ? new AdminPrincipal(demoUser)
                : new DemoPrincipal(demoUser);
        subject.getPrincipals().add(principal);
        return new UserAuthentication("demo", new DefaultUserIdentity(subject, principal, new String[0]));
    }

    private DemoUser fetchUserInfo(String username) {
        return new DemoUser(username);
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

package org.actioncontrollerdemo.servlet;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Redirects all http requests to https, except for those to localhost
 */
public class SecureConnectionFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        if (req.getServerName().equals("localhost")) {
            chain.doFilter(req, response);
            return;
        }

        if (isLocalClient(req)) {
            String redirectRequest = req.getScheme() + "://localhost:" + req.getLocalPort() + req.getRequestURI();
            if (req.getQueryString() != null) {
                redirectRequest += "?" + req.getQueryString();
            }
            resp.sendRedirect(redirectRequest);
            return;
        } else if (req.getScheme().equals("http")) {
            String redirectRequest = "https://" + req.getRemoteHost() + req.getRequestURI();
            if (req.getQueryString() != null) {
                redirectRequest += "?" + req.getQueryString();
            }
            resp.sendRedirect(redirectRequest);
            return;
        }
        chain.doFilter(req, response);
    }

    private boolean isLocalClient(HttpServletRequest req) throws UnknownHostException {
        InetAddress remoteAddr = InetAddress.getByName(req.getRemoteAddr());
        return remoteAddr.isLoopbackAddress() || remoteAddr.isAnyLocalAddress() || InetAddress.getLocalHost().getHostAddress().equals(remoteAddr.getHostAddress());
    }

    @Override
    public void destroy() {

    }
}

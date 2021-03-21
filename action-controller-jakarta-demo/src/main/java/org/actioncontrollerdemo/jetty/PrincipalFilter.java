package org.actioncontrollerdemo.jetty;

import org.eclipse.jetty.server.Request;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;

public class PrincipalFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        ((Request)request).setAuthentication(new DemoDeferredAuthentication());
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {

    }
}

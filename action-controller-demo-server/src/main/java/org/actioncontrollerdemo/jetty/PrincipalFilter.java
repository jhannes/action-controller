package org.actioncontrollerdemo.jetty;

import org.eclipse.jetty.server.Request;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
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

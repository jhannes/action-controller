package org.fakeservlet.jakarta;

import org.junit.Test;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class ServletTest {

    private final TestServlet servlet = new TestServlet();
    private final FakeJakartaContainer container = new FakeJakartaContainer("https://example.com/foo", "/servlet");

    private static class TestServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setHeader("query", req.getQueryString());
            resp.setIntHeader("port", req.getServerPort());
            resp.getWriter().write(req.getRequestURL().toString());
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            String remoteUser = req.getRemoteUser();
            resp.addCookie(new Cookie("user", remoteUser));
            resp.sendRedirect("/login");
        }

        @Override
        protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            try (BufferedReader reader = req.getReader()) {
                int sum = reader.lines().map(Integer::parseInt).reduce(0, Integer::sum);
                resp.setIntHeader("sum", sum);
            }
        }
    }
    
    
    @Test
    public void shouldServiceRequest() throws IOException, ServletException {
        FakeJakartaRequest request = container.newRequest("GET", "/hello/world");
        request.addParameter("firstName", "Jane");
        request.addParameter("lastName", "Doe");

        FakeJakartaResponse response = request.service(servlet);

        assertThat(response.getHeader("query")).isEqualTo("firstName=Jane&lastName=Doe");
        assertThat(response.getHeader("port")).isEqualTo("443");
        assertThat(response.getBodyString()).isEqualTo("https://example.com/foo/servlet/hello/world");
        
        // sendError
    }
    
    @Test
    public void shouldReadPort() throws IOException, ServletException {
        FakeJakartaRequest request = new FakeJakartaRequest("GET", new URL("http://example.com:8080/foo"), "/servlet", null);
        FakeJakartaResponse response = request.service(servlet);
        assertThat(response.getHeader("port")).isEqualTo("8080");
        assertThat(response.getBodyString()).isEqualTo("http://example.com:8080/foo/servlet");
    }

    @Test
    public void shouldSetCookie() throws IOException, ServletException {
        FakeJakartaRequest request = container.newRequest("POST", null);
        request.setUserPrincipal(() -> "User name");
        FakeJakartaResponse response = request.service(servlet);
        
        assertThat(response.getCookie("user")).isEqualTo("User name");
        assertThat(response.getHeader("location")).isEqualTo(container.getContextRoot() + "/login");
        assertThat(response.getStatus()).isEqualTo(302);
    }

    @Test
    public void shouldPutBody() throws ServletException, IOException {
        FakeJakartaRequest request = container.newRequest("PUT", null);
        request.setRequestBody("4\n4\n5");
        FakeJakartaResponse response = request.service(servlet);
        assertThat(response.getHeader("SUM")).isEqualTo("13");
    }
    
    @Test
    public void shouldReturn405OnUnsupportedMethods() throws ServletException, IOException {
        FakeJakartaRequest request = container.newRequest("DELETE", null);
        FakeJakartaResponse response = request.service(servlet);
        assertThat(response.getStatus()).isEqualTo(405);
    }
}

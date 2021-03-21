package org.fakeservlet;

import org.junit.Test;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class ServletTest {

    private final TestServlet servlet = new TestServlet();
    private final URL contextRootUrl = new URL("https://example.com/foo");

    public ServletTest() throws MalformedURLException {
    }

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
        FakeServletRequest request = new FakeServletRequest("GET", contextRootUrl, "/servlet", "/hello/world");
        request.addParameter("firstName", "Jane");
        request.addParameter("lastName", "Doe");

        FakeServletResponse response = request.service(servlet);

        assertThat(response.getHeader("query")).isEqualTo("firstName=Jane&lastName=Doe");
        assertThat(response.getHeader("port")).isEqualTo("443");
        assertThat(response.getBodyString()).isEqualTo("https://example.com/foo/servlet/hello/world");
        
        // sendError
    }
    
    @Test
    public void shouldReadPort() throws IOException, ServletException {
        FakeServletRequest request = new FakeServletRequest("GET", new URL("http://example.com:8080/foo"), "/servlet", null);
        FakeServletResponse response = request.service(servlet);
        assertThat(response.getHeader("port")).isEqualTo("8080");
        assertThat(response.getBodyString()).isEqualTo("http://example.com:8080/foo/servlet");
    }

    @Test
    public void shouldSetCookie() throws IOException, ServletException {
        FakeServletRequest request = new FakeServletRequest("POST", contextRootUrl, "/servlet", null);
        request.setUserPrincipal(() -> "User name");
        FakeServletResponse response = request.service(servlet);
        
        assertThat(response.getCookie("user")).isEqualTo("User name");
        assertThat(response.getHeader("location")).isEqualTo(contextRootUrl + "/login");
        assertThat(response.getStatus()).isEqualTo(302);
    }

    @Test
    public void shouldPutBody() throws ServletException, IOException {
        FakeServletRequest request = new FakeServletRequest("PUT", contextRootUrl, "/servlet", null);
        request.setRequestBody("4\n4\n5");
        FakeServletResponse response = request.service(servlet);
        assertThat(response.getHeader("SUM")).isEqualTo("13");
    }
    
    @Test
    public void shouldReturn405OnUnsupportedMethods() throws ServletException, IOException {
        FakeServletRequest request = new FakeServletRequest("DELETE", contextRootUrl, "/servlet", null);
        FakeServletResponse response = request.service(servlet);
        assertThat(response.getStatus()).isEqualTo(405);
    }
}

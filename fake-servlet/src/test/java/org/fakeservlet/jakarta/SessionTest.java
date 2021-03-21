package org.fakeservlet.jakarta;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionTest {

    private final TestServlet servlet = new TestServlet();
    private final FakeJakartaContainer container = new FakeJakartaContainer("https://example.com/foo", "/servlet");

    private static class TestServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            HttpSession session = req.getSession();
            for (String attributeName : Collections.list(session.getAttributeNames())) {
                resp.getWriter().write(attributeName + "=" + session.getAttribute(attributeName));
            }
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
            Collections.list(req.getAttributeNames())
                    .forEach(attributeName -> req.getSession().setAttribute(attributeName, req.getAttribute(attributeName)));
        }

        @Override
        protected void doDelete(HttpServletRequest req, HttpServletResponse resp) {
            req.getSession().invalidate();
        }
    }
    
    @Test
    public void shouldAddToSession() throws IOException, ServletException {
        FakeJakartaRequest postRequest = container.newRequest("POST", null);
        postRequest.setAttribute("sessionKey", "sessionValue");
        postRequest.service(servlet);

        HttpSession session = postRequest.getSession();

        FakeJakartaRequest getRequest = container.newRequest("GET", "/sessionKey");
        getRequest.setSession(session);
        FakeJakartaResponse response = getRequest.service(servlet);
        assertThat(response.getBodyString()).isEqualTo("sessionKey=sessionValue");
    }

    @Test
    public void shouldDeleteSession() throws ServletException, IOException {
        FakeJakartaRequest postRequest = container.newRequest("POST", null);
        postRequest.setAttribute("sessionKey", "sessionValue");
        postRequest.service(servlet);

        HttpSession session = postRequest.getSession();
        FakeJakartaRequest deleteRequest = container.newRequest("DELETE", null);
        deleteRequest.setSession(session);
        deleteRequest.service(servlet);

        FakeJakartaRequest getRequest = container.newRequest("GET", "/sessionKey");
        getRequest.setSession(session);
        FakeJakartaResponse response = postRequest.service(servlet);
        assertThat(response.getBodyString()).isEmpty();
    }
}

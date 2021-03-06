package org.fakeservlet;

import org.fakeservlet.jakarta.FakeJakartaRequest;
import org.junit.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionTest {

    private final TestServlet servlet = new TestServlet();
    private final FakeServletContainer container = new FakeServletContainer("https://example.com/foo", "/servlet");

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
        FakeServletRequest postRequest = container.newRequest("POST", null);
        postRequest.setAttribute("sessionKey", "sessionValue");
        postRequest.service(servlet);

        HttpSession session = postRequest.getSession();

        FakeServletRequest getRequest = container.newRequest("GET", "/sessionKey");
        getRequest.setSession(session);
        FakeServletResponse response = getRequest.service(servlet);
        assertThat(response.getBodyString()).isEqualTo("sessionKey=sessionValue");
    }

    @Test
    public void shouldDeleteSession() throws ServletException, IOException {
        FakeServletRequest postRequest = container.newRequest("POST", null);
        postRequest.setAttribute("sessionKey", "sessionValue");
        postRequest.service(servlet);

        HttpSession session = postRequest.getSession();
        FakeServletRequest deleteRequest = container.newRequest("DELETE", null);
        deleteRequest.setSession(session);
        deleteRequest.service(servlet);

        FakeServletRequest getRequest = container.newRequest("GET", "/sessionKey");
        getRequest.setSession(session);
        FakeServletResponse response = postRequest.service(servlet);
        assertThat(response.getBodyString()).isEmpty();
    }
}

package org.fakeservlet.jakarta;

import org.fakeservlet.FakeServletConfig;
import org.fakeservlet.FakeServletContainer;
import org.junit.Test;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class FakeJakartaConfigTest {

    public static class TestServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.getWriter().write(getInitParameter("test"));
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.getWriter().write(Collections.list(getServletConfig().getInitParameterNames()).toString());
        }
    }

    @Test
    public void shouldInitializeServlet() throws ServletException, IOException {
        TestServlet testServlet = new TestServlet();
        FakeJakartaConfig config = new FakeJakartaConfig();
        String initVariable = UUID.randomUUID().toString();
        config.getServletContext().setInitParameter("test", initVariable);
        config.getServletContext().setInitParameter("foo", "bar");
        testServlet.init(config);

        FakeJakartaContainer container = new FakeJakartaContainer();
        assertThat(container.newRequest("GET", null).service(testServlet).getBodyString())
                .isEqualTo(initVariable);
        assertThat(container.newRequest("POST", null).service(testServlet).getBodyString())
                .isEqualTo("[test, foo]");
    }
}

package org.fakeservlet;

import org.junit.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class FakeServletConfigTest {

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
        FakeServletConfig config = new FakeServletConfig();
        String initVariable = UUID.randomUUID().toString();
        config.getServletContext().setInitParameter("test", initVariable);
        config.getServletContext().setInitParameter("foo", "bar");
        testServlet.init(config);

        FakeServletContainer container = new FakeServletContainer();
        assertThat(container.newRequest("GET", null).service(testServlet).getBodyString())
                .isEqualTo(initVariable);
        assertThat(container.newRequest("POST", null).service(testServlet).getBodyString())
                .isEqualTo("[test, foo]");
    }
}

package org.fakeservlet;

import org.junit.Test;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import java.net.MalformedURLException;
import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FakeServletContextTest {

    private final FakeServletContext context = new FakeServletContext();

    public static class TestServlet extends HttpServlet {
    }

    public static class InvalidServlet extends HttpServlet {
        public InvalidServlet(String unused) {}
    }

    public static class TestListener implements ServletContextListener {

        @Override
        public void contextInitialized(ServletContextEvent sce) {
            sce.getServletContext().addServlet("testServlet", TestServlet.class.getName());
        }
    }

    public static class InvalidListener implements ServletContextListener {
        public InvalidListener(String unused) {}
    }

    @Test
    public void shouldInitializeListener() throws ServletException {
        context.addListener(TestListener.class.getName());
        assertThat(context.getServlet("testServlet"))
                .isInstanceOf(TestServlet.class);
    }

    @Test
    public void shouldHandleInvalidListeners() {
        assertThatThrownBy(() -> context.addListener(this.getClass().getName()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> context.addListener(InvalidListener.class))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> context.addListener("NoSuchClass"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldHandleInvalidServlet() {
        assertThatThrownBy(() -> context.addServlet("test", this.getClass().getName()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> context.addServlet("test", InvalidServlet.class))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> context.addServlet("test", "NoSuchServlet"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldReturnVersion() {
        assertThat(context.getMajorVersion()).isEqualTo(4);
        assertThat(context.getEffectiveMajorVersion()).isEqualTo(4);
        assertThat(context.getMinorVersion()).isEqualTo(0);
        assertThat(context.getEffectiveMinorVersion()).isEqualTo(0);
        assertThat(context.getVirtualServerName()).isEqualTo("fake-server");
        assertThat(context.getServerInfo()).isEqualTo("fake-server/4.0");
    }

    @Test
    public void hasNoResources() throws MalformedURLException {
        assertThat(context.getResource("/index.html")).isNull();
        assertThat(context.getResourceAsStream("/index.html")).isNull();
        assertThat(context.getResourcePaths("/")).isNull();
    }

    @Test
    public void shouldReturnInitParameters() {
        context.setInitParameter("foo", "one");
        context.setInitParameter("bar", "two");
        assertThat(context.getInitParameter("foo")).isEqualTo("one");
        assertThat(Collections.list(context.getInitParameterNames()))
                .contains("foo", "bar");
    }

    @Test
    public void shouldReturnAttributes() {
        Instant now = Instant.now();
        context.setAttribute("foo", now);
        context.setAttribute("bar", "something else");
        assertThat(context.getAttribute("foo")).isEqualTo(now);
        assertThat(Collections.list(context.getAttributeNames()))
                .contains("foo", "bar");
        context.removeAttribute("foo");
        assertThat(Collections.list(context.getAttributeNames()))
                .doesNotContain("foo");
    }



}

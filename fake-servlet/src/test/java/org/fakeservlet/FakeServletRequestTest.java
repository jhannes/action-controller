package org.fakeservlet;

import org.junit.Before;
import org.junit.Test;

import java.net.URL;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class FakeServletRequestTest {

    private FakeServletRequest request;

    @Before
    public void setUp() throws Exception {
        request = new FakeServletRequest("GET", new URL("http://example.com/demo"), "/servlet", null);
    }

    @Test
    public void shouldReturnParameterValues() {
        request.setParameter("theName", "the value");
        assertThat(request.getParameterValues("theName")).isEqualTo(new String[] { "the value" });
        assertThat(request.getParameterValues("missing")).isNull();
    }

    @Test
    public void shouldReturnParameterMap() {
        request.setParameter("first", "value1");
        request.setParameter("second", "value2");
        assertThat(request.getParameterMap())
            .containsEntry("first", new String[] { "value1" })
            .containsEntry("second", new String[] { "value2" });
    }

    @Test
    public void shouldReturnParameterNames() {
        request.setParameter("one", "value1");
        request.setParameter("another", "value1");
        assertThat(Collections.list(request.getParameterNames()))
                .containsExactlyInAnyOrder("one", "another");
    }

    @Test
    public void shouldReturnAttribueNames() {
        request.setAttribute("one", new Object());
        request.setAttribute("another", "value1");
        assertThat(Collections.list(request.getAttributeNames()))
                .containsExactlyInAnyOrder("one", "another");
    }

    @Test
    public void shouldReturnHeaders() {
        request.addHeader("X-My-Header", "First value");
        request.addHeader("X-My-Header", "Second value");
        request.addHeader("Content-Length", "301130");

        assertThat(Collections.list(request.getHeaderNames()))
                .containsOnlyOnce("X-My-Header");
        assertThat(Collections.list(request.getHeaders("X-My-Header")))
                .containsOnlyOnce("First value", "Second value");
        assertThat(request.getIntHeader("Content-Length")).isEqualTo(301130);
    }
}

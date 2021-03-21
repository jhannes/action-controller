package org.fakeservlet.jakarta;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class FakeServletRequestTest {

    private FakeJakartaRequest request;

    @Before
    public void setUp() throws Exception {
        FakeJakartaContainer container = new FakeJakartaContainer("http://example.com/demo", "/servlet");
        request = container.newRequest("GET", null);
    }

    @Test
    public void shouldReturnParameterValues() {
        request.addParameter("theName", "the value");
        assertThat(request.getParameterValues("theName")).isEqualTo(new String[] { "the value" });
        assertThat(request.getParameterValues("missing")).isNull();
    }

    @Test
    public void shouldReturnParameterMap() {
        request.addParameter("first", "value1");
        request.addParameter("second", "value2");
        assertThat(request.getParameterMap())
            .containsEntry("first", new String[] { "value1" })
            .containsEntry("second", new String[] { "value2" });
    }

    @Test
    public void shouldReturnParameterNames() {
        request.addParameter("one", "value1");
        request.addParameter("another", "value1");
        assertThat(Collections.list(request.getParameterNames()))
                .containsExactlyInAnyOrder("one", "another");
    }

    @Test
    public void shouldReturnAttributeNames() {
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
    
    @Test
    public void shouldReturnUser() {
        request.setUser("username", Arrays.asList("reader", "writer"));
        assertThat(request.getRemoteUser()).isEqualTo("username");
        assertThat(request.isUserInRole("writer")).isTrue();
        assertThat(request.isUserInRole("admin")).isFalse();
    }

    @Test
    public void shouldReturnForNoUser() {
        assertThat(request.getRemoteUser()).isNull();
        assertThat(request.isUserInRole("writer")).isFalse();
    }
}

package org.fakeservlet;

import org.junit.Before;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FakeServletRequestTest {

    private FakeServletRequest request;

    @Before
    public void setUp() throws Exception {
        FakeServletContainer container = new FakeServletContainer("http://example.com/demo", "/servlet");
        request = container.newRequest("GET", null);
    }

    @Test
    public void shouldReturnParameterValues() {
        request.addParameter("theName", "the value");
        assertThat(request.getParameterValues("theName")).isEqualTo(new String[]{"the value"});
        assertThat(request.getParameterValues("missing")).isNull();
    }

    @Test
    public void shouldReturnParameterMap() {
        request.addParameter("first", "value1");
        request.addParameter("second", "value2");
        assertThat(request.getParameterMap())
                .containsEntry("first", new String[]{"value1"})
                .containsEntry("second", new String[]{"value2"});
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
        ZonedDateTime now = ZonedDateTime.now();
        request.addHeader("X-My-Header", "First value");
        request.addHeader("X-My-Header", "Second value");
        request.addHeader("Content-Length", "301130");
        request.addHeader("If-Modified-Since", DateTimeFormatter.RFC_1123_DATE_TIME.format(now));

        assertThat(Collections.list(request.getHeaderNames()))
                .containsOnlyOnce("X-My-Header");
        assertThat(Collections.list(request.getHeaders("X-My-Header")))
                .containsOnlyOnce("First value", "Second value");
        assertThat(request.getIntHeader("Content-Length")).isEqualTo(301130);
        assertThat(request.getDateHeader("If-Modified-Since"))
                .isEqualTo(now.toInstant().truncatedTo(ChronoUnit.SECONDS).toEpochMilli());

        assertThat(request.getIntHeader("missing")).isEqualTo(-1);
        assertThat(request.getDateHeader("missing")).isEqualTo(-1);
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

    @Test
    public void shouldReturnCookies() {
        request.addCookie("foo", "bar");
        assertThat(request.getCookies()).hasOnlyOneElementSatisfying(cookie -> {
            assertThat(cookie.getName()).isEqualTo("foo");
            assertThat(cookie.getValue()).isEqualTo("bar");
        });
    }

    @Test
    public void doesNotSupportAsyncContext() {
        assertThat(request.isAsyncSupported()).isFalse();
        assertThat(request.isAsyncStarted()).isFalse();
        FakeServletResponse response = new FakeServletResponse(request);
        assertThatThrownBy(() -> request.startAsync(request, response))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> request.getAsyncContext())
                .isInstanceOf(IllegalStateException.class);
    }
}

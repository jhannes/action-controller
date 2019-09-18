package org.actioncontroller;

import org.actioncontroller.servlet.ApiServlet;
import org.fakeservlet.FakeServletRequest;
import org.fakeservlet.FakeServletResponse;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class ContentLocationHeaderTest {

    public static class Controller {
        @Post("/one")
        @ContentLocationHeader
        public String creatingMethod(
                @RequestParam("id") Optional<String> id
        ) {
            return "/two/" + id.orElse("1234");
        }

        @Post("/two/:id")
        @ContentLocationHeader
        public URL referingMethod(@PathParam("id") String id) throws MalformedURLException {
            return new URL("https://server.example.com:20443/hello/world");
        }
    }

    private ApiServlet servlet = new ApiServlet(new Controller());

    @Before
    public void setup() throws ServletException {
        servlet.init(null);
    }

    @Test
    public void shouldCombineParameterWithReturn() throws IOException, ServletException {
        FakeServletResponse resp = new FakeServletResponse();
        servlet.service(
                new FakeServletRequest("POST", new URL("http://my.example.com:8080/my/context"), "/actions", "/one"),
                resp);

        resp.assertNoError();
        assertThat(resp.getHeader("Content-location"))
                .isEqualTo("http://my.example.com:8080/my/context/actions/two/1234");
    }

    @Test
    public void shouldReturnUrl() throws IOException, ServletException {
        FakeServletResponse resp = new FakeServletResponse();
        servlet.service(
                new FakeServletRequest("POST", new URL("http://my.example.com:8080/my/context"), "/actions", "/two/1234"),
                resp);

        resp.assertNoError();
        assertThat(resp.getHeader("Content-location"))
                .isEqualTo("https://server.example.com:20443/hello/world");
    }


}

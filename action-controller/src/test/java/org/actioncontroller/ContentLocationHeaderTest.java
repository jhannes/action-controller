package org.actioncontroller;

import org.junit.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class ContentLocationHeaderTest {

    public static class Controller {
        @Post("/one")
        @ContentLocationHeader
        public String creatingMethod() {
            return "/two/1234";
        }

        @Post("/two/:id")
        @ContentLocationHeader
        public URL referingMethod() throws MalformedURLException {
            return new URL("https://server.example.com:20443/hello/world");
        }
    }

    private ApiServlet servlet = new ApiServlet() {
        {
            registerController(new Controller());
            verifyNoExceptions();
        }
    };


    @Test
    public void shouldCombineParameterWithReturn() throws IOException {
        FakeServletResponse resp = new FakeServletResponse();
        servlet.service(
                new FakeServletRequest("POST", new URL("http://my.example.com:8080/my/context"), "/actions", "/one"),
                resp);

        resp.assertNoError();
        assertThat(resp.getHeader("Content-location"))
                .isEqualTo("http://my.example.com:8080/my/context/actions/two/1234");
    }

    @Test
    public void shouldReturnUrl() throws IOException {
        FakeServletResponse resp = new FakeServletResponse();
        servlet.service(
                new FakeServletRequest("POST", new URL("http://my.example.com:8080/my/context"), "/actions", "/two/1234"),
                resp);

        resp.assertNoError();
        assertThat(resp.getHeader("Content-location"))
                .isEqualTo("https://server.example.com:20443/hello/world");
    }


}

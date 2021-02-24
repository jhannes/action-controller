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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class ContentLocationHeaderTest {

    public static class Controller {
        @POST("/one")
        @ContentLocationHeader
        public String creatingMethod(
                @RequestParam("id") Optional<String> id
        ) {
            return "/two/" + id.orElse("1234");
        }

        @POST("/two/:id")
        @ContentLocationHeader
        public URL referingMethod(@PathParam("id") String id) throws MalformedURLException {
            return new URL("https://server.example.com:20443/hello/world");
        }

        @POST("/three/")
        @ContentLocationHeader("/three/{threeId}/data")
        public UUID newId(@RequestParam("id") UUID id) {
            return id;
        }
    }

    private final URL contextRoot = new URL("http://my.example.com:8080/my/context");

    private final ApiServlet servlet = new ApiServlet(new Controller());

    public ContentLocationHeaderTest() throws MalformedURLException {
    }

    @Before
    public void setup() throws ServletException {
        servlet.init(null);
    }

    @Test
    public void shouldCombineParameterWithReturn() throws IOException, ServletException {
        FakeServletRequest req = new FakeServletRequest("POST", contextRoot, "/actions", "/one");
        FakeServletResponse resp = req.service(servlet);

        resp.assertNoError();
        assertThat(resp.getHeader("Content-location"))
                .isEqualTo("http://my.example.com:8080/my/context/actions/two/1234");
    }

    @Test
    public void shouldReturnUrl() throws IOException, ServletException {
        FakeServletRequest req = new FakeServletRequest("POST", contextRoot, "/actions", "/two/1234");
        FakeServletResponse resp = req.service(servlet);

        resp.assertNoError();
        assertThat(resp.getHeader("Content-location"))
                .isEqualTo("https://server.example.com:20443/hello/world");
    }

    @Test
    public void shouldFormatContentLocationPath() throws IOException, ServletException {
        UUID id = UUID.randomUUID();
        FakeServletRequest request = new FakeServletRequest("POST", contextRoot, "/actions", "/three");
        request.setParameter("id", id.toString());
        FakeServletResponse resp = request.service(servlet);

        resp.assertNoError();
        assertThat(resp.getHeader("Content-location"))
                .isEqualTo("http://my.example.com:8080/my/context/actions/three/" + id + "/data");
    }


}

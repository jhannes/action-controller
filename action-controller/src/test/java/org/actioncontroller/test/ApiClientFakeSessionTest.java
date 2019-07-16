package org.actioncontroller.test;

import org.actioncontroller.AbstractApiClientSessionTest;
import org.actioncontroller.servlet.ApiServlet;
import org.junit.Before;

import java.net.URL;

public class ApiClientFakeSessionTest extends AbstractApiClientSessionTest {

    @Before
    public void createServerAndClient() throws Exception {
        baseUrl = "http://example.com/test" + "/api";
        final LoginController controller = new LoginController();

        ApiServlet servlet = new ApiServlet() {
            @Override
            public void init() {
                registerController(controller);
            }
        };
        servlet.init(null);
        final URL contextRoot = new URL("http://example.com/test");
        client = ApiClientProxy.create(LoginController.class,
                new FakeApiClient(contextRoot, "/api", servlet));
    }
}

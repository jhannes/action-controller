package org.actioncontroller.test;

import org.actioncontroller.AbstractApiClientSessionTest;
import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.servlet.ApiServlet;
import org.junit.Before;

import java.net.URL;

public class ApiClientFakeSessionTest extends AbstractApiClientSessionTest {

    @Before
    public void createServerAndClient() throws Exception {
        baseUrl = "http://example.com/test" + "/api";
        ApiServlet servlet = new ApiServlet(new LoginController());
        servlet.init(null);
        final URL contextRoot = new URL("http://example.com/test");
        client = ApiClientClassProxy.create(LoginController.class,
                new FakeApiClient(contextRoot, "/api", servlet));
    }
}

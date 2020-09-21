package org.actioncontroller.test;

import org.actioncontroller.AbstractApiClientSessionTest;
import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.servlet.ApiServlet;
import org.junit.Before;

import java.net.URL;
import java.security.Principal;

public class ApiClientFakeSessionTest extends AbstractApiClientSessionTest {

    private FakeApiClient apiClient;

    @Before
    public void createServerAndClient() throws Exception {
        baseUrl = "http://example.com/test" + "/api";
        ApiServlet servlet = new ApiServlet(new LoginController());
        servlet.init(null);
        final URL contextRoot = new URL("http://example.com/test");
        apiClient = new FakeApiClient(contextRoot, "/api", servlet);
        client = ApiClientClassProxy.create(LoginController.class, apiClient);
    }

    @Override
    public void doAuthenticate(Principal principal) {
        apiClient.authenticate(principal);
    }
}

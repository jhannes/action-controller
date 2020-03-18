package org.actioncontroller;

import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.servlet.ApiServlet;
import org.actioncontroller.test.FakeApiClient;
import org.junit.Before;

import java.net.URL;

public class FakeServletHttpPrincipalTest extends AbstractHttpPrincipalTest {

    @Before
    public void createServerAndClient() throws Exception {
        ApiServlet servlet = new ApiServlet(new AuthenticatedController());
        servlet.init(null);
        final URL contextRoot = new URL("http://example.com/test");
        FakeApiClient apiClient = new FakeApiClient(contextRoot, "/api", servlet);
        client = ApiClientClassProxy.create(AuthenticatedController.class, apiClient);
    }
}

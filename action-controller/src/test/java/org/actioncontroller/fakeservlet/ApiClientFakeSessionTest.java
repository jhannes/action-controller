package org.actioncontroller.fakeservlet;

import org.actioncontroller.AbstractApiClientSessionTest;
import org.actioncontroller.client.ApiClient;
import org.actioncontroller.servlet.ApiServlet;
import org.actioncontroller.servlet.FakeServletClient;

import java.net.URL;
import java.security.Principal;

public class ApiClientFakeSessionTest extends AbstractApiClientSessionTest {

    private FakeServletClient apiClient;

    @Override
    protected ApiClient createClient(Object controller) throws Exception {
        baseUrl = "http://example.com/test" + "/api";
        ApiServlet servlet = new ApiServlet(new LoginController());
        servlet.init(null);
        final URL contextRoot = new URL("http://example.com/test");
        apiClient = new FakeServletClient(contextRoot, "/api", servlet);
        return apiClient;
    }

    @Override
    public void doAuthenticate(Principal principal) {
        apiClient.authenticate(principal);
    }
}

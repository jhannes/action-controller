package org.actioncontroller.jakarta;

import org.actioncontroller.AbstractApiClientSessionTest;
import org.actioncontroller.client.ApiClient;

import java.net.URL;
import java.security.Principal;

public class ApiClientFakeJakartaSessionTest extends AbstractApiClientSessionTest {

    private FakeJakartaApiClient apiClient;

    @Override
    protected ApiClient createClient(Object controller) throws Exception {
        baseUrl = "http://example.com/test" + "/api";
        ApiJakartaServlet servlet = new ApiJakartaServlet(new LoginController());
        servlet.init(null);
        final URL contextRoot = new URL("http://example.com/test");
        apiClient = new FakeJakartaApiClient(contextRoot, "/api", servlet);
        return apiClient;
    }

    @Override
    public void doAuthenticate(Principal principal) {
        apiClient.authenticate(principal);
    }
}

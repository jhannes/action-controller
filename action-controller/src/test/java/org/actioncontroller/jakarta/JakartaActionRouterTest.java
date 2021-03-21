package org.actioncontroller.jakarta;

import org.actioncontroller.ApiControllerActionRouterTest;
import org.actioncontroller.client.ApiClient;

import java.net.URL;

public class JakartaActionRouterTest extends ApiControllerActionRouterTest {

    protected ApiClient createClient(TestController controller) throws Exception {
        final ApiJakartaServlet servlet = new ApiJakartaServlet(controller);
        servlet.init(null);
        return new FakeJakartaApiClient(new URL("http://example.com/test"), "/api", servlet);
    }

}

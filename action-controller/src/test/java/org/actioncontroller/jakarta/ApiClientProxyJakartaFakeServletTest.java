package org.actioncontroller.jakarta;

import org.actioncontroller.AbstractApiClientProxyTest;
import org.actioncontroller.client.ApiClient;

import java.net.URL;

public class ApiClientProxyJakartaFakeServletTest extends AbstractApiClientProxyTest {

    @Override
    protected ApiClient createClient(TestController controller) throws Exception {
        final ApiJakartaServlet servlet = new ApiJakartaServlet(controller);
        servlet.init(null);
        return new FakeJakartaApiClient(new URL("http://example.com/test"), "/api", servlet);
    }

    @Override
    public void shouldRethrowRuntimeExceptions() {

    }

}

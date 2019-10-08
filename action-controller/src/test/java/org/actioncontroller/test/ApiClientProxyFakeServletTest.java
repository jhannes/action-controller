package org.actioncontroller.test;

import org.actioncontroller.AbstractApiClientProxyTest;
import org.actioncontroller.client.ApiClientProxy;
import org.actioncontroller.servlet.ApiServlet;
import org.junit.Before;

import java.net.URL;

public class ApiClientProxyFakeServletTest extends AbstractApiClientProxyTest {

    @Before
    public void createServerAndClient() throws Exception {
        baseUrl = "http://example.com/test";
        final TestController controller = new TestController();
        final URL contextRoot = new URL(baseUrl);
        final ApiServlet servlet = new ApiServlet(controller);
        servlet.init(null);
        client = ApiClientProxy.create(TestController.class, new FakeApiClient(contextRoot, "/api", servlet));
    }
}

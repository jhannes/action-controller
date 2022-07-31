package org.actioncontroller.fakeservlet;

import org.actioncontroller.AbstractHttpPrincipalTest;
import org.actioncontroller.client.ApiClient;
import org.actioncontroller.servlet.ApiServlet;
import org.actioncontroller.servlet.FakeServletClient;

import java.net.URL;

public class HttpPrincipalFakeServletTest extends AbstractHttpPrincipalTest {

    @Override
    protected ApiClient createApiClient(Object controller) throws Exception {
        ApiServlet servlet = new ApiServlet(controller);
        servlet.init(null);
        return new FakeServletClient(new URL("http://example.com/test"), "/api", servlet);
    }

}

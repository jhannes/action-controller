package org.actioncontroller.https;

import org.actioncontroller.servlet.ApiServlet;
import org.actioncontroller.test.FakeApiClient;
import org.junit.Before;

import java.net.URL;

public class ApiClientProxyHttpsFakeServletTest extends AbstractApiClientProxyHttpsTest {

    @Before
    public void createServerAndClient() throws Exception {
        ApiServlet servlet = new ApiServlet(new CertificateController());
        servlet.init(null);
        apiClient = new FakeApiClient(new URL("https://example.com/test"), "/api", servlet);
    }

}

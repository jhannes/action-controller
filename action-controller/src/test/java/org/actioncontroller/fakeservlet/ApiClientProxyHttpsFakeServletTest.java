package org.actioncontroller.fakeservlet;

import org.actioncontroller.AbstractApiClientProxyHttpsTest;
import org.actioncontroller.servlet.ApiServlet;
import org.actioncontroller.servlet.FakeServletClient;
import org.junit.Before;

import java.net.URL;

public class ApiClientProxyHttpsFakeServletTest extends AbstractApiClientProxyHttpsTest {

    @Before
    public void createServerAndClient() throws Exception {
        ApiServlet servlet = new ApiServlet(new CertificateController());
        servlet.init(null);
        apiClient = new FakeServletClient(new URL("https://example.com/test"), "/api", servlet);
    }

}

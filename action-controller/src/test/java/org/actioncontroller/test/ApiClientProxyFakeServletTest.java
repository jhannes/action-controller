package org.actioncontroller.test;

import org.actioncontroller.test.ApiClientFakeServletProxy;
import org.junit.Before;

import java.net.URL;

public class ApiClientProxyFakeServletTest extends AbstractApiClientProxyTest {

    @Before
    public void createServerAndClient() throws Exception {
        client = ApiClientFakeServletProxy.create(new TestController(), new URL("http://example.com/test"), "/api");
    }
}

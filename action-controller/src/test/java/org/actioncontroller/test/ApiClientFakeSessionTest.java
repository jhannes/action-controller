package org.actioncontroller.test;

import org.actioncontroller.AbstractApiClientSessionTest;
import org.junit.Before;

import java.net.URL;

public class ApiClientFakeSessionTest extends AbstractApiClientSessionTest {

    @Before
    public void createServerAndClient() throws Exception {
        baseUrl = "http://example.com/test" + "/api";
        client = ApiClientFakeServletProxy.create(new LoginController(),
                new URL("http://example.com/test"), "/api");
    }
}

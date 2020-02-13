package org.actioncontroller.test;

import org.actioncontroller.AbstractApiClientProxyTest;
import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.servlet.ApiServlet;
import org.junit.Before;
import org.junit.Test;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ApiClientProxyFakeServletTest extends AbstractApiClientProxyTest {

    @Before
    public void createServerAndClient() throws Exception {
        baseUrl = "http://example.com/test";
        final TestController controller = new TestController();
        final URL contextRoot = new URL(baseUrl);
        final ApiServlet servlet = new ApiServlet(controller);
        servlet.init(null);
        client = ApiClientClassProxy.create(TestController.class, new FakeApiClient(contextRoot, "/api", servlet));
    }

    @Override
    @Test
    public void shouldRethrowRuntimeExceptions() {
        /*
        expectedLogEvents.expect(
                ApiControllerAction.class,
                Level.ERROR,
                "While invoking TestController.divide(int,int)",
                new ArithmeticException("/ by zero")
        );
         */
        assertThatThrownBy(() -> client.divide(10, 0))
                .isInstanceOf(ArithmeticException.class)
                .hasMessageContaining("/ by zero");
    }
}

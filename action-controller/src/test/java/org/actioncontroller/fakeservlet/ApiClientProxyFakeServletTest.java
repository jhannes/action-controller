package org.actioncontroller.fakeservlet;

import org.actioncontroller.AbstractApiClientProxyTest;
import org.actioncontroller.ApiControllerAction;
import org.actioncontroller.client.ApiClient;
import org.actioncontroller.servlet.ApiServlet;
import org.actioncontroller.test.FakeApiClient;
import org.junit.Test;
import org.slf4j.event.Level;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ApiClientProxyFakeServletTest extends AbstractApiClientProxyTest {

    @Override
    protected ApiClient createClient(TestController controller) throws Exception {
        final ApiServlet servlet = new ApiServlet(controller);
        servlet.init(null);
        return new FakeApiClient(new URL("http://example.com/test"), "/api", servlet);
    }

    @Override
    @Test
    public void shouldRethrowRuntimeExceptions() {
        assertThatThrownBy(() -> controllerClient.divide(10, 0, false))
                .isInstanceOf(ArithmeticException.class)
                .hasMessageContaining("/ by zero");
    }
}

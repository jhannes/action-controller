package org.actioncontroller;

import org.actioncontroller.client.ApiClient;
import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.client.ApiClientExchange;
import org.actioncontroller.servlet.ApiServlet;
import org.actioncontroller.test.FakeApiClient;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class ApiControllerActionRouterTest {

    private TestController controllerClient;
    private ApiClient client;

    public static class TestController {
        
        @POST("/path")
        @ContentBody
        public String postToPath() {
            return "post";
        }
        
        @GET("/path")
        @ContentBody
        public String getToPath() {
            return "get";
        }
        
        @GET("/path?param")
        @ContentBody
        public String getWithParam(@RequestParam("param") String param) {
            return "get with param=" + param;
        }

        @GET("/callback?code")
        @ContentBody
        public String callbackWithCodeParam(@RequestParam("code") String error) {
            return "callbackWithErrorParam param=" + error;
        }

        @GET("/callback?error")
        @ContentBody
        public String callbackWithErrorParam(@RequestParam("error") String error) {
            return "callbackWithErrorParam param=" + error;
        }

        @GET("/path/default")
        @ContentBody
        public String getDefault() {
            return "getDefault";
        }
        
        @GET("/path/{param}")
        @ContentBody
        public String getWithPathParam(@PathParam("param") String param) {
            return "getWithPathParam " + param;
        }
        
        @GET("/path/{param}/otherConstant")
        @ContentBody
        public String getOtherConstant(@PathParam("param") String param) {
            return "getOtherConstant(" + param + ")";
        }

        @GET("/path/{param}/constant/otherConstant")
        @ContentBody
        public String getParamInTheMiddle(@PathParam("param") String param) {
            return "getParamInTheMiddle(" + param + ")";
        }

        @GET("/path/constant/{param}/{otherParam}")
        @ContentBody
        public String getParamAtEnd(@PathParam("param") String param, @PathParam("otherParam") String otherParam) {
            return "getParamAtEnd(" + param + "," + otherParam + ")";
        }
    }

    protected ApiClient createClient(TestController controller) throws Exception {
        final ApiServlet servlet = new ApiServlet(controller);
        servlet.init(null);
        return new FakeApiClient(new URL("http://example.com/test"), "/api", servlet);
    }


    @Before
    public void setUp() throws Exception {
        client = createClient(new TestController());
        controllerClient = ApiClientClassProxy.create(TestController.class, client);
    }

    @Test
    public void shouldRouteOnHttpMethod() {
        assertThat(controllerClient.getToPath()).isEqualTo("get");
        assertThat(controllerClient.postToPath()).isEqualTo("post");
    }
    
    @Test
    public void shouldRouteOnParameter() {
        assertThat(controllerClient.getToPath()).isEqualTo("get");
        assertThat(controllerClient.getWithParam("value")).isEqualTo("get with param=value");
    }
    
    @Test
    public void shouldPreferParamWithConstantValue() {
        assertThat(controllerClient.getDefault()).isEqualTo("getDefault");
        assertThat(controllerClient.getWithPathParam("param")).isEqualTo("getWithPathParam param");
        assertThat(controllerClient.getWithPathParam("default"))
                .as("Router will prefer path with constant value")
                .isEqualTo("getDefault");
    }
    
    @Test
    public void shouldPreferEarlierConstant() {
        assertThat(controllerClient.getParamAtEnd("foo", "bar")).isEqualTo("getParamAtEnd(foo,bar)");
        assertThat(controllerClient.getParamInTheMiddle("foo")).isEqualTo("getParamInTheMiddle(foo)");
        assertThat(controllerClient.getParamAtEnd("constant", "otherConstant")).isEqualTo("getParamAtEnd(constant,otherConstant)");

        assertThat(controllerClient.getParamInTheMiddle("constant"))
                .as("should route to getParamAtEnd since the middle variable matches an early constant")
                .isEqualTo("getParamAtEnd(constant,otherConstant)");
    }
    
    @Test
    public void shouldPickCorrectSubconstantUnderPathVariable() {
        assertThat(controllerClient.getParamInTheMiddle("foo")).isEqualTo("getParamInTheMiddle(foo)");
        assertThat(controllerClient.getOtherConstant("foo")).isEqualTo("getOtherConstant(foo)");
    }
    
    @Test
    public void shouldThrowOnNoMatchingParameterRoute() throws IOException {
        ApiClientExchange exchange = client.createExchange();
        exchange.setTarget("GET", "/callback");
        exchange.executeRequest();
        assertThat(exchange.getResponseCode()).isEqualTo(404);
    }
    
    @Test
    public void shouldThrowOnNoLeafRoute() throws IOException {
        ApiClientExchange exchange = client.createExchange();
        exchange.setTarget("GET", "/path/foo/noMatch");
        exchange.executeRequest();
        assertThat(exchange.getResponseCode()).isEqualTo(404);
    }
    
}
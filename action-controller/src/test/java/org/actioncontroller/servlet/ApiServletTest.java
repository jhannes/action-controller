package org.actioncontroller.servlet;

import org.actioncontroller.ApiControllerAction;
import org.actioncontroller.ApiControllerActionRouter;
import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.ApiControllerRouteMap;
import org.actioncontroller.ContentBody;
import org.actioncontroller.GET;
import org.actioncontroller.HttpActionException;
import org.actioncontroller.HttpRedirectException;
import org.actioncontroller.HttpRequestException;
import org.actioncontroller.POST;
import org.actioncontroller.PathParam;
import org.actioncontroller.RequestParam;
import org.actioncontroller.RequireUserRole;
import org.actioncontroller.SessionParameter;
import org.actioncontroller.jmx.ApiControllerActionMXBeanAdaptor;
import org.actioncontroller.json.JsonBody;
import org.actioncontroller.meta.ApiHttpExchange;
import org.fakeservlet.FakeServletContainer;
import org.fakeservlet.FakeServletRequest;
import org.fakeservlet.FakeServletResponse;
import org.jsonbuddy.JsonObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.extend.junit.ExpectedLogEventsRule;
import org.slf4j.event.Level;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.annotation.ElementType;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public class ApiServletTest {

    private final ApiServlet servlet = new ApiServlet();
    private final StringWriter responseBody = new StringWriter();
    public JsonObject postedBody;
    public Optional<Boolean> admin;
    public int amount;
    private final FakeServletContainer container = new FakeServletContainer("http://example.com/root", "/servlet");

    public class ExampleController {
        @GET("")
        @JsonBody
        public JsonObject one(@RequestParam("name") Optional<String> name) {
            return new JsonObject().put("name", name.orElse("Anonymous"));
        }

        @GET("/error")
        public void throwError() {
            throw new HttpActionException(401, "You are not authorized");
        }

        @GET("/user/:userId/message/:messageId")
        public URL privateMethod(
                @PathParam("userId") UUID userId,
                @PathParam("messageId") String messageId
        ) throws MalformedURLException {
            return new URL("https://messages.example.com/?user=" + userId + "&message=" + messageId);
        }

        @GET("/restricted")
        @RequireUserRole("admin")
        @JsonBody
        public JsonObject restrictedOperation() {
            return new JsonObject().put("message", "you're in!");
        }

        @POST("/postMethod")
        public void postAction(@JsonBody JsonObject o) {
            postedBody = o;
        }

        @POST("/mappingByType")
        public void mappingByType(ApiHttpExchange exchange) {

        }

        @POST("/setLoggedInUser")
        public void sessionUpdater(@SessionParameter("username") Consumer<String> usernameSetter) {
            usernameSetter.accept("Alice Bobson");
        }

        @GET("/redirect")
        @ContentBody
        public String redirector() {
            throw new HttpRedirectException("/login");
        }
    }

    public class ControllerWithTypedParameters {
        private UUID uuid;
        private long longValue;
        private ElementType enumValue;

        @GET("/hello")
        public void methodWithOptionalBoolean(
                @RequestParam("admin") Optional<Boolean> adminParam,
                @RequestParam.ClientIp String clientIp
        ) {
            admin = adminParam;
        }

        @GET("/goodbye")
        public void methodWithRequiredInt(@RequestParam("amount") int amountParam) {
            amount = amountParam;
        }

        @POST("/withUuid")
        public void methodWithUuid(@RequestParam("uuid") UUID uuid) {
            this.uuid = uuid;
        }

        @POST("/withLong")
        public void methodWithLong(@RequestParam("longValue") long longValue) {
            this.longValue = longValue;
        }

        @POST("/withEnum")
        public void methodWithEnum(@RequestParam("enumValue") ElementType enumValue) {
            this.enumValue = enumValue;
        }
    }

    @Test
    public void shouldCallMethodWithArgumentsAndConvertReturn() throws IOException, ServletException {
        String name = UUID.randomUUID().toString();
        FakeServletRequest request = container.newRequest("GET", null);
        request.addParameter("name", name);

        FakeServletResponse response = request.service(servlet);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(JsonObject.parse(response.getBodyString()).requiredString("name")).isEqualTo(name);
        assertThat(response.getContentType()).isEqualTo("application/json");
    }

    @Test
    public void shouldOutputErrorToResponse() throws IOException, ServletException {
        FakeServletRequest request = container.newRequest("GET", "/error");

        FakeServletResponse response = request.service(servlet);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getStatusMessage()).isEqualTo("You are not authorized");
    }

    @Test
    public void shouldGive404OnUnknownAction() throws IOException, ServletException {
        FakeServletRequest request = container.newRequest("GET", "/missing");
        expectedLogEvents.expectPattern(ApiControllerRouteMap.class, Level.INFO, "No route for {}. Routes {}");
        FakeServletResponse response = request.service(servlet);
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    public void shouldDecodePathParams() throws IOException, ServletException {
        UUID userId = UUID.randomUUID();
        FakeServletRequest request = container.newRequest("GET", "/user/" + userId + "/message/abc");
        FakeServletResponse response = request.service(servlet);
        assertThat(response.getHeader("Location")).isEqualTo("https://messages.example.com/?user=" + userId + "&message=abc");
    }

    @Test
    public void shouldSendRedirect() throws IOException, ServletException {
        FakeServletRequest request = container.newRequest("GET", "/redirect");
        FakeServletResponse response = request.service(servlet);
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo(container.getContextRoot() + "/login");
    }

    @Test
    public void shouldPostJson() throws IOException, ServletException {
        FakeServletRequest request = container.newRequest("POST", "/postMethod");
        JsonObject requestObject = new JsonObject()
                .put("foo", "bar")
                .put("list", Arrays.asList("a", "b", "c"));
        request.setRequestBody(requestObject.toIndentedJson(" "));
        FakeServletResponse response = request.service(servlet);
        assertThat(postedBody).isEqualTo(requestObject);
    }

    @Test
    public void shouldGive400OnMalformedJson() throws IOException, ServletException {
        FakeServletRequest request = container.newRequest("POST", "/postMethod");

        request.setRequestBody("This is not JSON!");

        expectedLogEvents.expect(ApiControllerAction.class, Level.WARN,
                "While processing ServletHttpExchange[POST " + container.getServletPath() + "/postMethod] arguments for ApiControllerMethodAction{POST /postMethod => ExampleController.postAction(JsonObject)}");
        FakeServletResponse response = request.service(servlet);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getStatusMessage()).contains("org.jsonbuddy.parse.JsonParseException: Unexpected character 'T'");
    }

    @Test
    public void shouldCallWithOptionalParameter() throws IOException, ServletException {
        FakeServletRequest request = container.newRequest("GET", "/hello");

        assertThat(admin).isNull();
        FakeServletResponse response = request.service(servlet);
        assertThat(admin).isEmpty();

        request.addParameter("admin", "true");
        servlet.service(request, response);
        assertThat(admin).hasValue(true);
    }


    @Test
    public void shouldCallWithRequiredInt() throws IOException, ServletException {
        FakeServletRequest request = container.newRequest("GET", "/goodbye");
        request.addParameter("amount", "123");
        request.service(servlet);
        assertThat(amount).isEqualTo(123);
    }

    @Test
    public void shouldGive400OnParameterConversion() throws IOException, ServletException {
        FakeServletRequest request = container.newRequest("GET", "/goodbye");
        request.addParameter("amount", "one");

        FakeServletResponse response = request.service(servlet);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getStatusMessage()).isEqualTo("Could not convert amount=[one] to int");
    }

    @Test
    public void shouldGive400OnInvalidUuidConversion() throws IOException, ServletException {
        FakeServletRequest request = container.newRequest("POST", "/withUuid");
        request.addParameter("uuid", "Not an uuid");

        expectedLogEvents.expect(ApiControllerAction.class, Level.DEBUG,
                "While processing ServletHttpExchange[POST " + container.getServletPath() + "/withUuid?uuid=Not+an+uuid]" +
                " arguments to ApiControllerMethodAction{POST /withUuid => ControllerWithTypedParameters.methodWithUuid(UUID)}: " +
                new HttpRequestException("Could not convert uuid=[Not an uuid] to java.util.UUID"));
        FakeServletResponse response = request.service(servlet);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getStatusMessage()).contains("Could not convert uuid=[Not an uuid] to java.util.UUID");
    }

    @Test
    public void shouldGive400OnInvalidLongConversion() throws IOException, ServletException {
        FakeServletRequest request = container.newRequest("POST", "/withLong");
        request.addParameter("longValue", "one hundred");

        expectedLogEvents.expect(ApiControllerAction.class, Level.DEBUG,
                "While processing ServletHttpExchange[POST " + container.getServletPath() + "/withLong?longValue=one+hundred] arguments to " +
                "ApiControllerMethodAction{POST /withLong => ControllerWithTypedParameters.methodWithLong(long)}: " +
                new HttpRequestException("Could not convert longValue=[one hundred] to long"));
        FakeServletResponse response = request.service(servlet);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getStatusMessage()).contains("Could not convert longValue=[one hundred] to long");
    }

    @Rule
    public ExpectedLogEventsRule expectedLogEvents = new ExpectedLogEventsRule(Level.WARN);

    @Test
    public void shouldGive400OnInvalidEnumConversion() throws IOException, ServletException {
        FakeServletRequest request = container.newRequest("POST", "/withEnum");
        request.addParameter("enumValue", "unknown");

        expectedLogEvents.expect(ApiControllerAction.class, Level.DEBUG,
                "While processing ServletHttpExchange[POST " + container.getServletPath() + "/withEnum?enumValue=unknown] arguments to " +
                "ApiControllerMethodAction{POST /withEnum => ControllerWithTypedParameters.methodWithEnum(ElementType)}: " +
                new HttpRequestException("Value 'unknown' not in [TYPE, FIELD, METHOD, PARAMETER, CONSTRUCTOR, LOCAL_VARIABLE, ANNOTATION_TYPE, PACKAGE, TYPE_PARAMETER, TYPE_USE, MODULE]"));
        FakeServletResponse response = request.service(servlet);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getStatusMessage()).contains("Value 'unknown' not in [TYPE, FIELD, METHOD, PARAMETER, CONSTRUCTOR, LOCAL_VARIABLE, ANNOTATION_TYPE, PACKAGE, TYPE_PARAMETER, TYPE_USE, MODULE]");
    }

    @Test
    public void shouldRequireNonOptionalParameter() throws IOException, ServletException {
        FakeServletRequest request = container.newRequest("GET", "/goodbye");
        expectedLogEvents.expect(ApiControllerAction.class, Level.DEBUG,
                "While processing ServletHttpExchange[GET " + container.getServletPath() + "/goodbye] arguments to " +
                "ApiControllerMethodAction{GET /goodbye => ControllerWithTypedParameters.methodWithRequiredInt(int)}: " +
                new HttpRequestException("Missing required parameter amount"));
        FakeServletResponse response = request.service(servlet);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getStatusMessage()).isEqualTo("Missing required parameter amount");
    }

    @Test
    public void shouldReportParameterConversionFailure() throws IOException, ServletException {
        FakeServletRequest request = container.newRequest("GET", "/goodbye");
        request.addParameter("amount", "one");

        expectedLogEvents.expectMatch(expect -> expect
                .level(Level.DEBUG).logger(ApiControllerAction.class)
                .pattern("While processing {} arguments to {}: {}")
        );
        FakeServletResponse response = request.service(servlet);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getStatusMessage()).isEqualTo("Could not convert amount=[one] to int");
    }

    @Test
    public void shouldSetSessionParameters() throws IOException, ServletException {
        FakeServletRequest request = container.newRequest("POST", "/setLoggedInUser");
        FakeServletResponse response = request.service(servlet);
        assertThat(request.getSession().getAttribute("username")).isEqualTo("Alice Bobson");
    }

    @Test
    public void shouldRejectUnauthenticedUsersFromRestrictedOperation() throws IOException, ServletException {
        FakeServletRequest request = container.newRequest("GET", "/restricted");

        FakeServletResponse response = request.service(servlet);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(JsonObject.parse(response.getBodyString()).requiredString("message"))
                .isEqualTo("Login required");
    }

    @Test
    public void shouldAllowUserWithCorrectRole() throws IOException, ServletException {
        FakeServletRequest request = container.newRequest("GET", "/restricted");
        request.setUser("good user", Collections.singletonList("admin"));
        FakeServletResponse response = request.service(servlet);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(JsonObject.parse(response.getBodyString()).requiredString("message"))
                .isEqualTo("you're in!");
    }

    @Test
    public void shouldRejectUserWithoutCorrectRole() throws IOException, ServletException {
        FakeServletRequest request = container.newRequest("GET", "/restricted");
        request.setUser("silly user", Collections.emptyList());
        FakeServletResponse response = request.service(servlet);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(JsonObject.parse(response.getBodyString()).requiredString("message"))
                .isEqualTo("Insufficient permissions");
    }

    @Test
    public void shouldCreateMBeans() throws MalformedObjectNameException {
        MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
        servlet.registerMBeans(mbeanServer);

        assertThat(mbeanServer.queryMBeans(new ObjectName("org.actioncontroller:controller=" + ExampleController.class.getName() + ",action=one"), null))
                .anySatisfy(a -> assertThat(a.getClassName()).contains("ApiControllerActionMXBeanAdaptor"));
    }

    @Test
    public void shouldQueryMBean() {
        ApiControllerAction action = ApiControllerActionRouter.createActions(new ExampleController(), new ApiControllerContext())
                .stream().filter(a -> a.getAction().getName().equals("postAction")).findFirst().orElseThrow();
        ApiControllerActionMXBeanAdaptor mbean = new ApiControllerActionMXBeanAdaptor(action);
        assertThat(mbean.getHttpMethod()).isEqualTo("POST");
        assertThat(mbean.getPath()).isEqualTo("/postMethod");
    }


    @Before
    public void setupRequest() throws ServletException {
        servlet.registerControllers(new ExampleController(), new ControllerWithTypedParameters());
        servlet.init(null);
    }
}

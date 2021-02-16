package org.actioncontroller.servlet;

import org.actioncontroller.ApiControllerAction;
import org.actioncontroller.ApiControllerActionRouter;
import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.ContentBody;
import org.actioncontroller.GET;
import org.actioncontroller.HttpActionException;
import org.actioncontroller.HttpRedirectException;
import org.actioncontroller.HttpRequestException;
import org.actioncontroller.POST;
import org.actioncontroller.PathParam;
import org.actioncontroller.RequestParam;
import org.actioncontroller.RequireUserRole;
import org.actioncontroller.ApiControllerRouteMap;
import org.actioncontroller.SessionParameter;
import org.actioncontroller.jmx.ApiControllerActionMXBeanAdaptor;
import org.actioncontroller.json.JsonBody;
import org.actioncontroller.meta.ApiHttpExchange;
import org.fakeservlet.FakeServletRequest;
import org.fakeservlet.FakeServletResponse;
import org.jsonbuddy.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.extend.junit.ExpectedLogEventsRule;
import org.mockito.Mockito;
import org.slf4j.event.Level;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.annotation.ElementType;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ApiServletTest {

    private ApiServlet servlet = new ApiServlet();
    private HttpServletRequest requestMock = Mockito.mock(HttpServletRequest.class);
    private HttpServletResponse responseMock = Mockito.mock(HttpServletResponse.class);
    private StringWriter responseBody = new StringWriter();
    public JsonObject postedBody;
    public Optional<Boolean> admin;
    public int amount;
    private URL contextRoot;
    private FakeServletResponse response = new FakeServletResponse();

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
    public void shouldCallMethodWithArgumentsAndConvertReturn() throws IOException {
        String name = UUID.randomUUID().toString();
        FakeServletRequest request = new FakeServletRequest("GET", contextRoot, "/api", null);
        request.setParameter("name", name);

        servlet.service(request, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(JsonObject.parse(new String(response.getBody())).requiredString("name")).isEqualTo(name);
        assertThat(response.getContentType()).isEqualTo("application/json");
    }

    @Test
    public void shouldOutputErrorToResponse() throws IOException {
        when(requestMock.getMethod()).thenReturn("GET");
        when(requestMock.getPathInfo()).thenReturn("/error");

        servlet.service(requestMock, responseMock);
        verify(responseMock).sendError(401, "You are not authorized");
    }

    @Test
    public void shouldGive404OnUnknownAction() throws IOException {
        FakeServletRequest request = new FakeServletRequest("GET", contextRoot, "/api", "/missing");
        expectedLogEvents.expectPattern(ApiControllerRouteMap.class, Level.INFO, "No route for {}. Routes {}");
        servlet.service(request, response);
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    public void shouldDecodePathParams() throws IOException {
        UUID userId = UUID.randomUUID();
        when(requestMock.getMethod()).thenReturn("GET");
        when(requestMock.getPathInfo()).thenReturn("/user/" + userId + "/message/abc");
        servlet.service(requestMock, responseMock);
        verify(responseMock).sendRedirect("https://messages.example.com/?user=" + userId + "&message=abc");
    }

    @Test
    public void shouldSendRedirect() throws IOException {
        when(requestMock.getScheme()).thenReturn("https");
        when(requestMock.getServerName()).thenReturn("messages.example.com");
        when(requestMock.getServerPort()).thenReturn(443);
        when(requestMock.getContextPath()).thenReturn("");
        when(requestMock.getServletPath()).thenReturn("");
        when(requestMock.getMethod()).thenReturn("GET");
        when(requestMock.getPathInfo()).thenReturn("/redirect");
        servlet.service(requestMock, responseMock);
        verify(responseMock).sendRedirect("/login");
    }

    @Test
    public void shouldPostJson() throws IOException {
        when(requestMock.getMethod()).thenReturn("POST");
        when(requestMock.getPathInfo()).thenReturn("/postMethod");

        JsonObject requestObject = new JsonObject()
                .put("foo", "bar")
                .put("list", Arrays.asList("a", "b", "c"));
        when(requestMock.getReader())
            .thenReturn(new BufferedReader(new StringReader(requestObject.toIndentedJson(" "))));
        servlet.service(requestMock, responseMock);

        assertThat(postedBody).isEqualTo(requestObject);
    }

    @Test
    public void shouldGive400OnMalformedJson() throws IOException {
        FakeServletRequest request = new FakeServletRequest("POST", contextRoot, "/api", "/postMethod");

        request.setRequestBody("This is not JSON!");

        expectedLogEvents.expect(ApiControllerAction.class, Level.WARN,
                "While processing ServletHttpExchange[POST " + contextRoot + "/api/postMethod] arguments for ApiControllerMethodAction{POST /postMethod => ExampleController.postAction(JsonObject)}");
        servlet.service(request, response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getStatusMessage()).contains("org.jsonbuddy.parse.JsonParseException: Unexpected character 'T'");
    }

    @Test
    public void shouldCallWithOptionalParameter() throws IOException {
        FakeServletRequest request = new FakeServletRequest("GET", contextRoot, "/api", "/hello");

        assertThat(admin).isNull();
        servlet.service(request, response);
        assertThat(admin).isEmpty();

        request.setParameter("admin", "true");
        servlet.service(request, response);
        assertThat(admin).hasValue(true);
    }


    @Test
    public void shouldCallWithRequiredInt() throws IOException {
        when(requestMock.getMethod()).thenReturn("GET");
        when(requestMock.getPathInfo()).thenReturn("/goodbye");

        when(requestMock.getParameter("amount")).thenReturn("123");
        servlet.service(requestMock, responseMock);
        assertThat(amount).isEqualTo(123);
    }

    @Test
    public void shouldGive400OnParameterConversion() throws IOException {
        FakeServletRequest request = new FakeServletRequest("GET", contextRoot, "/api", "/goodbye");
        request.setParameter("amount", "one");

        servlet.service(request, response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getStatusMessage()).isEqualTo("Could not convert amount=one to int");
    }

    @Test
    public void shouldGive400OnInvalidUuidConversion() throws IOException {
        FakeServletRequest request = new FakeServletRequest("POST", contextRoot, "/api", "/withUuid");
        request.setParameter("uuid", "Not an uuid");

        expectedLogEvents.expect(ApiControllerAction.class, Level.DEBUG,
                "While processing ServletHttpExchange[POST " + contextRoot + "/api/withUuid?uuid=Not+an+uuid]" +
                " arguments to ApiControllerMethodAction{POST /withUuid => ControllerWithTypedParameters.methodWithUuid(UUID)}: " +
                new HttpRequestException("Could not convert uuid=Not an uuid to java.util.UUID"));
        servlet.service(request, response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getStatusMessage()).contains("Could not convert uuid=Not an uuid to java.util.UUID");
    }

    @Test
    public void shouldGive400OnInvalidLongConversion() throws IOException {
        FakeServletRequest request = new FakeServletRequest("POST", contextRoot, "/api", "/withLong");
        request.setParameter("longValue", "one hundred");

        expectedLogEvents.expect(ApiControllerAction.class, Level.DEBUG,
                "While processing ServletHttpExchange[POST " + contextRoot + "/api/withLong?longValue=one+hundred] arguments to " +
                "ApiControllerMethodAction{POST /withLong => ControllerWithTypedParameters.methodWithLong(long)}: " +
                new HttpRequestException("Could not convert longValue=one hundred to long"));
        servlet.service(request, response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getStatusMessage()).contains("Could not convert longValue=one hundred to long");
    }

    @Rule
    public ExpectedLogEventsRule expectedLogEvents = new ExpectedLogEventsRule(Level.WARN);

    @Test
    public void shouldGive400OnInvalidEnumConversion() throws IOException {
        FakeServletRequest request = new FakeServletRequest("POST", contextRoot, "/api", "/withEnum");
        request.setParameter("enumValue", "unknown");

        expectedLogEvents.expect(ApiControllerAction.class, Level.DEBUG,
                "While processing ServletHttpExchange[POST " + contextRoot + "/api/withEnum?enumValue=unknown] arguments to " +
                "ApiControllerMethodAction{POST /withEnum => ControllerWithTypedParameters.methodWithEnum(ElementType)}: " +
                new HttpRequestException("Could not convert enumValue=unknown to java.lang.annotation.ElementType"));
        servlet.service(request, response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getStatusMessage()).contains("Could not convert enumValue=unknown to java.lang.annotation.ElementType");
    }

    @Test
    public void shouldRequireNonOptionalParameter() throws IOException {
        FakeServletRequest request = new FakeServletRequest("GET", contextRoot, "/api", "/goodbye");
        expectedLogEvents.expect(ApiControllerAction.class, Level.DEBUG,
                "While processing ServletHttpExchange[GET " + contextRoot + "/api/goodbye] arguments to " +
                "ApiControllerMethodAction{GET /goodbye => ControllerWithTypedParameters.methodWithRequiredInt(int)}: " +
                new HttpRequestException("Missing required parameter amount"));
        servlet.service(request, response);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getStatusMessage()).isEqualTo("Missing required parameter amount");
    }

    @Test
    public void shouldReportParameterConversionFailure() throws IOException {
        FakeServletRequest request = new FakeServletRequest("GET", contextRoot, "/api", "/goodbye");
        request.setParameter("amount", "one");

        expectedLogEvents.expectMatch(expect -> expect
                .level(Level.DEBUG).logger(ApiControllerAction.class)
                .pattern("While processing {} arguments to {}: {}")
        );
        servlet.service(request, response);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getStatusMessage()).isEqualTo("Could not convert amount=one to int");
    }

    @Test
    public void shouldSetSessionParameters() throws IOException {
        when(requestMock.getMethod()).thenReturn("POST");
        when(requestMock.getPathInfo()).thenReturn("/setLoggedInUser");

        HttpSession mockSession = Mockito.mock(HttpSession.class);
        when(requestMock.getSession(Mockito.anyBoolean())).thenReturn(mockSession);

        servlet.service(requestMock, responseMock);
        verify(mockSession).setAttribute("username", "Alice Bobson");
    }

    @Test
    public void shouldRejectUnauthenticedUsersFromRestrictedOperation() throws IOException {
        when(requestMock.getMethod()).thenReturn("GET");
        when(requestMock.getPathInfo()).thenReturn("/restricted");
        servlet.service(requestMock, responseMock);

        verify(responseMock).setStatus(401);
        verify(responseMock).setContentType("application/json");
        verify(responseMock).getCharacterEncoding();
        verify(responseMock).setCharacterEncoding(null);
        verify(responseMock).getWriter();
        assertThat(JsonObject.parse(responseBody.toString()).requiredString("message"))
                .isEqualTo("Login required");
    }

    @Test
    public void shouldAllowUserWithCorrectRole() throws IOException {
        when(requestMock.getRemoteUser()).thenReturn("good user");
        when(requestMock.isUserInRole("admin")).thenReturn(true);

        when(requestMock.getMethod()).thenReturn("GET");
        when(requestMock.getPathInfo()).thenReturn("/restricted");
        servlet.service(requestMock, responseMock);

        verify(responseMock).setContentType("application/json");
        verify(responseMock).getCharacterEncoding();
        verify(responseMock).setCharacterEncoding(null);
        verify(responseMock).getWriter();
        assertThat(JsonObject.parse(responseBody.toString()).requiredString("message"))
                .isEqualTo("you're in!");
    }

    @Test
    public void shouldRejectUserWithoutCorrectRole() throws IOException {
        when(requestMock.getRemoteUser()).thenReturn("silly user");
        when(requestMock.isUserInRole("guest")).thenReturn(false);
        when(requestMock.getMethod()).thenReturn("GET");
        when(requestMock.getPathInfo()).thenReturn("/restricted");
        servlet.service(requestMock, responseMock);

        verify(responseMock).setStatus(403);
        verify(responseMock).setContentType("application/json");
        verify(responseMock).getCharacterEncoding();
        verify(responseMock).setCharacterEncoding(null);
        verify(responseMock).getWriter();
        verifyNoMoreInteractions();
        assertThat(JsonObject.parse(responseBody.toString()).requiredString("message"))
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
    public void setupRequest() throws IOException, ServletException {
        servlet.registerControllers(new ExampleController(), new ControllerWithTypedParameters());
        servlet.init(null);

        when(responseMock.getWriter()).thenReturn(new PrintWriter(responseBody));
        contextRoot = new URL("http://example.com/root");
    }

    @After
    public void verifyNoMoreInteractions() {
        Mockito.verifyNoMoreInteractions(responseMock);
    }
}

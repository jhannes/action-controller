package org.actioncontroller.servlet;

import org.actioncontroller.ApiControllerAction;
import org.actioncontroller.Get;
import org.actioncontroller.HttpActionException;
import org.actioncontroller.HttpRequestException;
import org.actioncontroller.PathParam;
import org.actioncontroller.Post;
import org.actioncontroller.RequestParam;
import org.actioncontroller.RequireUserRole;
import org.actioncontroller.SendRedirect;
import org.actioncontroller.SessionParameter;
import org.actioncontroller.json.JsonBody;
import org.actioncontroller.meta.ApiHttpExchange;
import org.fakeservlet.FakeServletRequest;
import org.fakeservlet.FakeServletResponse;
import org.jsonbuddy.JsonObject;
import org.jsonbuddy.parse.JsonParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.extend.junit.ExpectedLogEventsRule;
import org.mockito.Mockito;
import org.slf4j.event.Level;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.annotation.ElementType;
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
        @Get("")
        @JsonBody
        public JsonObject one(@RequestParam("name") Optional<String> name) {
            return new JsonObject().put("name", name.orElse("Anonymous"));
        }

        @Get("/error")
        public void throwError() {
            throw new HttpActionException(401, "You are not authorized");
        }

        @Get("/user/:userId/message/:messageId")
        public URL privateMethod(
                @PathParam("userId") UUID userId,
                @PathParam("messageId") String messageId
        ) throws MalformedURLException {
            return new URL("https://messages.example.com/?user=" + userId + "&message=" + messageId);
        }

        @Get("/restricted")
        @RequireUserRole("admin")
        @JsonBody
        public JsonObject restrictedOperation() {
            return new JsonObject().put("message", "you're in!");
        }

        @Post("/postMethod")
        public void postAction(@JsonBody JsonObject o) {
            postedBody = o;
        }

        @Post("/mappingByType")
        public void mappingByType(ApiHttpExchange exchange) {

        }

        @Post("/setLoggedInUser")
        public void sessionUpdater(@SessionParameter("username") Consumer<String> usernameSetter) {
            usernameSetter.accept("Alice Bobson");
        }

        @Get("/redirect")
        @SendRedirect
        public String redirector() {
            return "login";
        }
    }

    public class ControllerWithTypedParameters {
        private UUID uuid;
        private long longValue;
        private ElementType enumValue;

        @Get("/hello")
        public void methodWithOptionalBoolean(
                @RequestParam("admin") Optional<Boolean> adminParam,
                @RequestParam.ClientIp String clientIp
        ) {
            admin = adminParam;
        }

        @Get("/goodbye")
        public void methodWithRequiredInt(@RequestParam("amount") int amountParam) {
            amount = amountParam;
        }

        @Post("/withUuid")
        public void methodWithUuid(@RequestParam("uuid") UUID uuid) {
            this.uuid = uuid;
        }

        @Post("/withLong")
        public void methodWithLong(@RequestParam("longValue") long longValue) {
            this.longValue = longValue;
        }

        @Post("/withEnum")
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
        assertThat(JsonObject.parse(response.getBody()).requiredString("name")).isEqualTo(name);
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
        expectedLogEvents.expect(ApiServlet.class, Level.WARN, "No route for GET " + contextRoot.getPath() + "/api[/missing]");
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
        when(requestMock.getHeader("Host")).thenReturn("messages.example.com");
        when(requestMock.getContextPath()).thenReturn("");
        when(requestMock.getServletPath()).thenReturn("");
        when(requestMock.getMethod()).thenReturn("GET");
        when(requestMock.getPathInfo()).thenReturn("/redirect");
        servlet.service(requestMock, responseMock);
        verify(responseMock).sendRedirect("https://messages.example.com/login");
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

        request.setReader(() -> new BufferedReader(new StringReader("This is not JSON!")));

        expectedLogEvents.expect(ApiControllerAction.class, Level.WARN,
                "While processing ServletHttpExchange[POST " + contextRoot + "/api/postMethod] arguments");
        servlet.service(request, response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getStatusMessage()).contains("org.jsonbuddy.parse.JsonParseException: Unexpected character 'T'");
    }

    @Test
    public void shouldCallWithOptionalParameter() throws IOException {
        when(requestMock.getMethod()).thenReturn("GET");
        when(requestMock.getPathInfo()).thenReturn("/hello");

        assertThat(admin).isNull();
        servlet.service(requestMock, responseMock);
        assertThat(admin).isEmpty();

        when(requestMock.getParameter("admin")).thenReturn("true");
        servlet.service(requestMock, responseMock);
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
        expectedLogEvents.expectPattern(ApiControllerAction.class, Level.WARN, "While processing {} arguments to {}");
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

        expectedLogEvents.expect(ApiControllerAction.class, Level.WARN,
                "While processing ServletHttpExchange[POST " + contextRoot + "/api/withUuid?uuid=Not+an+uuid] arguments to ApiControllerMethodAction{POST /withUuid => ControllerWithTypedParameters.methodWithUuid(UUID)}",
                new HttpRequestException("Could not convert uuid=Not an uuid to java.util.UUID"));
        servlet.service(request, response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getStatusMessage()).contains("Could not convert uuid=Not an uuid to java.util.UUID");
    }

    @Test
    public void shouldGive400OnInvalidLongConversion() throws IOException {
        FakeServletRequest request = new FakeServletRequest("POST", contextRoot, "/api", "/withLong");
        request.setParameter("longValue", "one hundred");

        expectedLogEvents.expect(ApiControllerAction.class, Level.WARN,
                "While processing ServletHttpExchange[POST " + contextRoot + "/api/withLong?longValue=one+hundred] arguments to ApiControllerMethodAction{POST /withLong => ControllerWithTypedParameters.methodWithLong(long)}",
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

        expectedLogEvents.expect(ApiControllerAction.class, Level.WARN,
                "While processing ServletHttpExchange[POST " + contextRoot + "/api/withEnum?enumValue=unknown] arguments to ApiControllerMethodAction{POST /withEnum => ControllerWithTypedParameters.methodWithEnum(ElementType)}",
                new HttpRequestException("Could not convert enumValue=unknown to java.lang.annotation.ElementType"));
        servlet.service(request, response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getStatusMessage()).contains("Could not convert enumValue=unknown to java.lang.annotation.ElementType");
    }

    @Test
    public void shouldRequireNonOptionalParameter() throws IOException {
        FakeServletRequest request = new FakeServletRequest("GET", contextRoot, "/api", "/goodbye");
        expectedLogEvents.expect(ApiControllerAction.class, Level.WARN,
                "While processing ServletHttpExchange[GET " + contextRoot + "/api/goodbye] arguments to ApiControllerMethodAction{GET /goodbye => ControllerWithTypedParameters.methodWithRequiredInt(int)}",
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
                .level(Level.WARN).logger(ApiControllerAction.class)
                .pattern("While processing {} arguments to {}")
                .exception(HttpRequestException.class, "Could not convert amount=one to int")
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

        verify(responseMock).sendError(401,"User must be logged in for public org.jsonbuddy.JsonObject org.actioncontroller.servlet.ApiServletTest$ExampleController.restrictedOperation()");
        verify(responseMock).setContentType("application/json");
        verify(responseMock).getCharacterEncoding();
        verify(responseMock).setCharacterEncoding(null);
        verify(responseMock).getWriter();
        assertThat(JsonParser.parseToObject(responseBody.toString()).requiredString("message"))
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
        assertThat(JsonParser.parseToObject(responseBody.toString()).requiredString("message"))
                .isEqualTo("you're in!");
    }

    @Test
    public void shouldRejectUserWithoutCorrectRole() throws IOException {
        when(requestMock.getRemoteUser()).thenReturn("silly user");
        when(requestMock.isUserInRole("guest")).thenReturn(false);
        when(requestMock.getMethod()).thenReturn("GET");
        when(requestMock.getPathInfo()).thenReturn("/restricted");
        servlet.service(requestMock, responseMock);

        verify(responseMock).sendError(403, "User failed to authenticate for public org.jsonbuddy.JsonObject org.actioncontroller.servlet.ApiServletTest$ExampleController.restrictedOperation(): Missing role admin for user");
        verify(responseMock).setContentType("application/json");
        verify(responseMock).getCharacterEncoding();
        verify(responseMock).setCharacterEncoding(null);
        verify(responseMock).getWriter();
        assertThat(JsonParser.parseToObject(responseBody.toString()).requiredString("message"))
                .isEqualTo("Insufficient permissions");
    }

    @Before
    public void setupRequest() throws IOException {
        servlet.registerController(new ExampleController());
        servlet.registerController(new ControllerWithTypedParameters());

        when(responseMock.getWriter()).thenReturn(new PrintWriter(responseBody));
        contextRoot = new URL("http://example.com/root");
    }

    @After
    public void verifyNoMoreInteractions() {
        Mockito.verifyNoMoreInteractions(responseMock);
    }
}

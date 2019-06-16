package org.actioncontroller;

import org.actioncontroller.json.JsonBody;
import org.actioncontroller.meta.ApiHttpExchange;
import org.jsonbuddy.JsonObject;
import org.jsonbuddy.parse.JsonParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    private class ExampleController {

        @Get("/one")
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

        @Post("/setLoggedInUser")
        public void sessionUpdater(@SessionParameter("username") Consumer<String> usernameSetter) {
            usernameSetter.accept("Alice Bobson");
        }

        @Post("/mappingByType")
        public void mappingByType(ApiHttpExchange exchange) {

        }

        @Get("/redirect")
        @SendRedirect
        public String redirector() {
            return "login";
        }
    }

    @Test
    public void shouldCallMethodWithArgumentsAndConvertReturn() throws IOException {
        String name = UUID.randomUUID().toString();
        when(requestMock.getMethod()).thenReturn("GET");
        when(requestMock.getPathInfo()).thenReturn("/one");
        when(requestMock.getParameter("name")).thenReturn(name);

        servlet.service(requestMock, responseMock);

        assertThat(JsonParser.parseToObject(responseBody.toString()).requiredString("name"))
            .isEqualTo(name);
        verify(responseMock).getWriter();
        verify(responseMock).setContentType("application/json");
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
        when(requestMock.getMethod()).thenReturn("GET");
        when(requestMock.getPathInfo()).thenReturn("/missing");
        servlet.service(requestMock, responseMock);
        verify(responseMock).sendError(eq(404), anyString());
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
    public void shouldGive400OnMisformedJson() throws IOException {
        when(requestMock.getMethod()).thenReturn("POST");
        when(requestMock.getPathInfo()).thenReturn("/postMethod");

        when(requestMock.getReader())
                .thenReturn(new BufferedReader(new StringReader("This is not JSON!")));
        servlet.service(requestMock, responseMock);
        verify(responseMock).sendError(eq(400), anyString());
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
        when(requestMock.getMethod()).thenReturn("GET");
        when(requestMock.getPathInfo()).thenReturn("/goodbye");

        when(requestMock.getParameter("amount")).thenReturn("one");
        servlet.service(requestMock, responseMock);

        verify(responseMock).sendError(eq(400), anyString());
    }

    @Test
    public void shouldRequireNonOptionalParameter() throws IOException {
        when(requestMock.getMethod()).thenReturn("GET");
        when(requestMock.getPathInfo()).thenReturn("/goodbye");

        servlet.service(requestMock, responseMock);
        verify(responseMock).sendError(400, "Missing required parameter amount");
    }

    @Test
    public void shouldReportParameterConversionFailure() throws IOException {
        when(requestMock.getMethod()).thenReturn("GET");
        when(requestMock.getPathInfo()).thenReturn("/goodbye");

        when(requestMock.getParameter("amount")).thenReturn("one");
        servlet.service(requestMock, responseMock);
        verify(responseMock).sendError(400, "Invalid parameter amount 'one' is not an int");
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

        verify(responseMock).sendError(401,"User must be logged in for public org.jsonbuddy.JsonObject org.actioncontroller.ApiServletTest$ExampleController.restrictedOperation()");
        verify(responseMock).setContentType("application/json");
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

        verify(responseMock).sendError(403, "User failed to authenticate for public org.jsonbuddy.JsonObject org.actioncontroller.ApiServletTest$ExampleController.restrictedOperation(): Missing role admin for user");
        verify(responseMock).setContentType("application/json");
        verify(responseMock).getWriter();
        assertThat(JsonParser.parseToObject(responseBody.toString()).requiredString("message"))
                .isEqualTo("Insufficient permissions");
    }

    @Before
    public void setupRequest() throws IOException {
        servlet.registerController(new ExampleController());

        when(responseMock.getWriter()).thenReturn(new PrintWriter(responseBody));
    }

    @After
    public void verifyNoMoreInteractions() {
        Mockito.verifyNoMoreInteractions(responseMock);
    }
}

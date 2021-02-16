package org.actioncontroller.json;

import org.actioncontroller.ContentBody;
import org.actioncontroller.GET;
import org.actioncontroller.RequestParam;
import org.actioncontroller.client.ApiClient;
import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.servlet.ApiServlet;
import org.actioncontroller.test.FakeApiClient;
import org.jsonbuddy.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class UnencryptedJsonCookieTest {

    private Controller apiClient;
    private ApiClient client;

    public static class Controller {

        @GET("/methodWithCookie")
        @ContentBody
        public String methodWithCookie(@UnencryptedJsonCookie("myCookie") JsonObject cookie) {
            return cookie.requiredString("message");
        }

        @GET("/methodWithPojo")
        @ContentBody
        public String methodWithCookie(@UnencryptedJsonCookie CookieValue cookie) {
            return cookie.name;
        }

        @GET("/methodWithMap")
        @ContentBody
        public String methodWithCookie(@UnencryptedJsonCookie("mapCookie") Map<String, String> cookie) {
            return cookie.get("message");
        }

        @GET("/methodUpdatingCookies")
        @ContentBody
        public String methodUpdatingCookie(
                @UnencryptedJsonCookie(value = "mapCookie", setInResponse = true) Map<String, String> mapCookie,
                @UnencryptedJsonCookie(setInResponse = true) CookieValue pojoCookie,
                @UnencryptedJsonCookie(value = "json", setInResponse = true) JsonObject jsonCookie,
                @RequestParam("username") String username
        ) {
            String oldName = mapCookie.get("username");
            mapCookie.put("username", username);
            pojoCookie.name = username;
            jsonCookie.put("username", username);
            return oldName;
        }
    }

    public static class CookieValue {
        public String name;
    }


    @Test
    public void shouldHandleJsonObject() {
        JsonObject cookie = new JsonObject().put("message", "Hello world");
        assertThat(apiClient.methodWithCookie(cookie)).isEqualTo("Hello world");
    }

    @Test
    public void shouldHandlePojo() {
        CookieValue value = new CookieValue();
        value.name = "Testing";
        assertThat(apiClient.methodWithCookie(value)).isEqualTo("Testing");
    }

    @Test
    public void shouldHandleMap() {
        Map<String, String> value = new HashMap<>();
        value.put("message", "Testing");
        assertThat(apiClient.methodWithCookie(value)).isEqualTo("Testing");
    }

    @Test
    public void shouldUpdateCookie() {
        Map<String, String> mapCookie = new HashMap<>();
        mapCookie.put("username", "oldName");
        CookieValue pojoCookie = new CookieValue();
        pojoCookie.name = "Test";
        apiClient.methodUpdatingCookie(mapCookie, pojoCookie, new JsonObject(), "newName");
        assertThat(parseJsonCookie("CookieValue").requiredString("name"))
                .isEqualTo("newName");
        assertThat(parseJsonCookie("mapCookie").requiredString("username"))
                .isEqualTo("newName");
        assertThat(parseJsonCookie("json").requiredString("username"))
                .isEqualTo("newName");
    }

    @Test
    public void shouldCreateCookie() {
        apiClient.methodUpdatingCookie(null, null, null, "newUserName");
        assertThat(parseJsonCookie("CookieValue").requiredString("name"))
                .isEqualTo("newUserName");
        assertThat(parseJsonCookie("mapCookie").requiredString("username"))
                .isEqualTo("newUserName");
        assertThat(parseJsonCookie("json").requiredString("username"))
                .isEqualTo("newUserName");
    }


    public JsonObject parseJsonCookie(String cookieName) {
        return JsonObject.parse(URLDecoder.decode(client.getClientCookie(cookieName), StandardCharsets.UTF_8));
    }

    @Before
    public void setUp() throws Exception {
        client = createClient(new Controller());
        this.apiClient = ApiClientClassProxy.create(Controller.class, client);
    }

    protected ApiClient createClient(Controller controller) throws ServletException, IOException {
        ApiServlet servlet = new ApiServlet(controller);
        servlet.init(null);
        return new FakeApiClient(new URL("http://example.com/test"), "/api", servlet);
    }

}

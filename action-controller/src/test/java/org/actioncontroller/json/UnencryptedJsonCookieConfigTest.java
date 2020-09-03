package org.actioncontroller.json;

import org.actioncontroller.POST;
import org.actioncontroller.servlet.ApiServlet;
import org.jsonbuddy.JsonObject;
import org.junit.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UnencryptedJsonCookieConfigTest {
    public static class ControllerWithUnnamedMapCookie {
        @POST("/something")
        public void doSomething(@UnencryptedJsonCookie Map<String, String> cookieMap) {
        }
    }

    @Test
    public void shouldFailOnMapCookieWithoutName() {
        assertThatThrownBy(() -> new ApiServlet(new ControllerWithUnnamedMapCookie()).init(null))
                .hasMessageContaining("Missing cookie name");
    }

    public static class ControllerWithUnnamedJsonCookie {
        @POST("/something")
        public void doSomething(@UnencryptedJsonCookie JsonObject cookieMap) {
        }
    }

    @Test
    public void shouldFailOnJsonCookieWithoutName() {
        assertThatThrownBy(() -> new ApiServlet(new ControllerWithUnnamedJsonCookie()).init(null))
                .hasMessageContaining("Missing cookie name");
    }

}

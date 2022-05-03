package org.actioncontroller.values.json;

import org.actioncontroller.actions.POST;
import org.actioncontroller.ApiControllerActionRouter;
import org.actioncontroller.servlet.ApiServlet;
import org.jsonbuddy.JsonObject;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.optional.junit.ExpectedLogEventsRule;
import org.slf4j.event.Level;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;


public class UnencryptedJsonCookieConfigTest {

    @Rule
    public ExpectedLogEventsRule expectedLogEventsRule = new ExpectedLogEventsRule(Level.WARN);

    public static class ControllerWithUnnamedMapCookie {
        @POST("/something")
        public void doSomething(@UnencryptedJsonCookie Map<String, String> cookieMap) {
        }
    }

    @Test
    public void shouldFailOnMapCookieWithoutName() {
        expectedLogEventsRule.expectPattern(ApiControllerActionRouter.class, Level.ERROR, "Failed to setup {}");
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
        expectedLogEventsRule.expectPattern(ApiControllerActionRouter.class, Level.ERROR, "Failed to setup {}");
        assertThatThrownBy(() -> new ApiServlet(new ControllerWithUnnamedJsonCookie()).init(null))
                .hasMessageContaining("Missing cookie name");
    }

}


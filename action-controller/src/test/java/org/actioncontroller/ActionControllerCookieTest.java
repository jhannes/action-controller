package org.actioncontroller;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ActionControllerCookieTest {

    @Test
    public void shouldBuildCookie() {
        assertThat(new ActionControllerCookie("cookieName", "value")
                .httpOnly(false).secure(true).path("/demo")
                .toStringRFC6265()
        ).isEqualTo("cookieName=value; Secure; Path=/demo");
    }

    @Test
    public void shouldSupportBooleanFields() {
        String example = "SID=31d4d96e407aad42; Path=/; Secure; HttpOnly";
        ActionControllerCookie cookie = ActionControllerCookie.parse(example);
        assertThat(cookie.getName()).isEqualTo("SID");
        assertThat(cookie.getValue()).isEqualTo("31d4d96e407aad42");
        assertThat(cookie.getAttribute("Secure")).isEqualTo(true);
        assertThat(cookie.toStringRFC6265()).isEqualTo(example);
    }

    @Test
    public void shouldSupportStringFields() {
        String example = "lang=en-US; Path=/; Domain=example.com";
        ActionControllerCookie cookie = ActionControllerCookie.parse(example);
        assertThat(cookie.getName()).isEqualTo("lang");
        assertThat(cookie.getAttribute("Secure")).isNull();
        assertThat(cookie.getAttribute("Path")).isEqualTo("/");
        assertThat(cookie.getAttribute("Domain")).isEqualTo("example.com");
        assertThat(cookie.toStringRFC6265()).isEqualTo(example);
    }

    @Test
    public void shouldSupportQuotedValues() {
        ActionControllerCookie cookie = ActionControllerCookie.parse("test=\"value\"; Path=/");
        assertThat(cookie.getValue()).isEqualTo("value");
    }

    @Test
    public void shouldDeleteCookie() {
        ActionControllerCookie cookie = ActionControllerCookie.delete("cookieName").path("/demo").httpOnly(true).secure(true);
        assertThat(cookie.toStringRFC6265())
                .isEqualTo("cookieName=; Expires=Thu, 1 Jan 1970 00:00:00 GMT; Max-Age=0; Path=/demo; HttpOnly; Secure");
    }

}

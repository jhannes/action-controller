package org.actioncontroller;

import org.junit.Test;

import java.util.List;

import static org.actioncontroller.ActionControllerCookie.parseCookieHeader;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ActionControllerCookieTest {

    @Test
    public void shouldBuildCookie() {
        ActionControllerCookie cookie = new ActionControllerCookie("cookieName", "value")
                .httpOnly(false).secure(true).path("/demo");
        assertThat(cookie.toStringRFC6265())
                .isEqualTo("cookieName=\"value\"; Secure; Path=/demo");
        assertThat(cookie.getValue()).isEqualTo("value");
    }

    @Test
    public void shouldSupportBooleanFields() {
        String example = "SID=\"31d4d96e407aad42\"; Path=/; Secure; HttpOnly";
        ActionControllerCookie cookie = ActionControllerCookie.parseSetCookieHeader(example);
        assertThat(cookie.getName()).isEqualTo("SID");
        assertThat(cookie.getValue()).isEqualTo("31d4d96e407aad42");
        assertThat(cookie.secure()).isTrue();
        assertThat(cookie.httpOnly()).isTrue();
        assertThat(cookie.toStringRFC6265()).isEqualTo(example);
        assertThat(ActionControllerCookie.parseSetCookieHeader(cookie.toStringRFC6265()))
                .isEqualTo(cookie);
    }

    @Test
    public void shouldSupportStringFields() {
        String example = "lang=\"en-US\"; Path=/; Domain=example.com";
        ActionControllerCookie cookie = ActionControllerCookie.parseSetCookieHeader(example);
        assertThat(cookie.getName()).isEqualTo("lang");
        assertThat(cookie.secure()).isFalse();
        assertThat(cookie.httpOnly()).isFalse();
        assertThat(cookie.getAttribute("Path")).isEqualTo("/");
        assertThat(cookie.getAttribute("Domain")).isEqualTo("example.com");
        assertThat(cookie.toStringRFC6265()).isEqualTo(example);
    }

    @Test
    public void shouldRejectEmptySetCookieHeader() {
        assertThatThrownBy(() -> ActionControllerCookie.parseSetCookieHeader("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldSupportQuotedValues() {
        ActionControllerCookie cookie = ActionControllerCookie.parseSetCookieHeader("test=\"value\"; Path=/");
        assertThat(cookie.getValue()).isEqualTo("value");
    }

    @Test
    public void shouldDeleteCookie() {
        ActionControllerCookie cookie = ActionControllerCookie.delete("cookieName").path("/demo").httpOnly(true).secure(true);
        assertThat(cookie.toStringRFC6265())
                .isEqualTo("cookieName=; Expires=Thu, 1 Jan 1970 00:00:00 GMT; Max-Age=0; Path=/demo; HttpOnly; Secure");
    }

    @Test
    public void shouldParseSerializedCookie() {
        ActionControllerCookie cookie = new ActionControllerCookie("key", "value").sameSite("Lax").httpOnly(true);
        assertThat(ActionControllerCookie.parseSetCookieHeader(cookie.toStringRFC6265()))
                .isEqualTo(cookie);
    }

    @Test
    public void shouldEscapeCookie() {
        String value = "space percent% semi; quote \"comma, backslash \\";
        ActionControllerCookie cookie = new ActionControllerCookie(
                "key",
                value
        );
        assertThat(cookie.toStringRFC6265()).doesNotContain(List.of(" ;,\\"));
        assertThat(cookie.toStringRFC6265()).isEqualTo("key=\"space+percent%25+semi%3B+quote+%22comma%2C+backslash+%5C\"");
        assertThat(ActionControllerCookie.parseSetCookieHeader(cookie.toStringRFC6265()).getValue())
                .isEqualTo(value);
    }

    @Test
    public void shouldParseWeirdCookies() {
        assertThat(ActionControllerCookie.parseSetCookieHeader("key=\"value\"").getValue()).isEqualTo("value");
        assertThat(ActionControllerCookie.parseSetCookieHeader("key=value").getValue()).isEqualTo("value");
        assertThat(ActionControllerCookie.parseSetCookieHeader("key=value with space").getValue()).isEqualTo("value with space");
        assertThat(ActionControllerCookie.parseSetCookieHeader("key=value+with+space").getValue()).isEqualTo("value with space");
        assertThat(ActionControllerCookie.parseSetCookieHeader("key=value%20with%20space").getValue()).isEqualTo("value with space");
        assertThat(ActionControllerCookie.parseSetCookieHeader("key=\"value with comma, semi; etc\"").getValue())
                .isEqualTo("value with comma, semi; etc");
    }

    @Test
    public void shouldParseClientCookieHeader() {
        assertThat(parseCookieHeader("json={\"key\": \"value\"}; other=value"))
                .containsEntry("other", "value")
                .containsEntry("json", "{\"key\": \"value\"}");

        assertThat(parseCookieHeader("  key1  = value1; key2=value2"))
                .containsEntry("key1", "value1")
                .containsEntry("key2", "value2");

        assertThat(parseCookieHeader("  key1  = value%20with%20space; key2=space+percent%25+semi%3B+quote+%22comma%2C+backslash+%5C"))
                .containsEntry("key1", "value with space")
                .containsEntry("key2", "space percent% semi; quote \"comma, backslash \\");

        assertThat(parseCookieHeader("key1=\"value1 ; with semi\"; key2= \"value2\""))
                .containsEntry("key2", "value2")
                .containsEntry("key1", "value1 ; with semi");
    }

    @Test
    public void shouldRejectUnparsableCookies() {
        assertThatThrownBy(() -> parseCookieHeader("no key")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parseCookieHeader("key=value;no key")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parseCookieHeader("key=\"unfinished quoted value")).isInstanceOf(IllegalArgumentException.class);
    }

}

package org.fakeservlet;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FakeCookieTest {

    @Test
    public void shouldBuildCookie() {
        assertThat(new FakeCookie("cookieName", "value")
                .httpOnly(false).secure(true).path("/demo")
                .toStringRFC6265()
        ).isEqualTo("cookieName=value; Secure; Path=/demo");
    }

    @Test
    public void shouldSupportBooleanFields() {
        String example = "SID=31d4d96e407aad42; Path=/; Secure; HttpOnly";
        FakeCookie cookie = FakeCookie.parse(example);
        assertThat(cookie.getName()).isEqualTo("SID");
        assertThat(cookie.getValue()).isEqualTo("31d4d96e407aad42");
        assertThat(cookie.getAttribute("Secure")).isEqualTo(true);
        assertThat(cookie.toStringRFC6265()).isEqualTo(example);
    }

    @Test
    public void shouldStringFields() {
        String example = "lang=en-US; Path=/; Domain=example.com";
        FakeCookie cookie = FakeCookie.parse(example);
        assertThat(cookie.getName()).isEqualTo("lang");
        assertThat(cookie.getAttribute("Secure")).isNull();
        assertThat(cookie.getAttribute("Path")).isEqualTo("/");
        assertThat(cookie.getAttribute("Domain")).isEqualTo("example.com");
        assertThat(cookie.toStringRFC6265()).isEqualTo(example);
    }

    @Test
    public void shouldSupportQuotedValues() {
        FakeCookie cookie = FakeCookie.parse("test=\"value\"; Path=/");
        assertThat(cookie.getValue()).isEqualTo("value");
    }

    @Test
    public void shouldDeleteCookie() {
        FakeCookie cookie = FakeCookie.delete("cookieName").path("/demo").httpOnly(true).secure(true);
        assertThat(cookie.toStringRFC6265())
                .isEqualTo("cookieName=; Expires=Thu, 1 Jan 1970 00:00:00 GMT; Max-Age=0; Path=/demo; HttpOnly; Secure");
    }

}

package org.actioncontrollerdemo;

import org.actioncontroller.actions.GET;
import org.actioncontroller.values.ContentBody;
import org.actioncontroller.values.HttpHeader;
import org.actioncontroller.values.RequestParam;
import org.actioncontroller.values.UnencryptedCookiePreview;
import org.actioncontroller.values.json.JsonBody;
import org.jsonbuddy.JsonObject;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class TestController {
    private final Runnable updater;

    public TestController() {
        this.updater = () -> {};
    }

    public TestController(Runnable updater) {
        this.updater = updater;
    }

    @GET("/test")
    @ContentBody
    public String sayHello(
            @RequestParam("name") Optional<String> name,
            @UnencryptedCookiePreview("value") AtomicReference<String> cookieValue
    ) {
        String user = name
                .or(() -> Optional.ofNullable(cookieValue.get()))
                .orElse("<no username>");

        name.ifPresentOrElse(cookieValue::set, () -> cookieValue.set(null));
        return "Hello " + user;
    }

    @GET("/update")
    public void update() {
        updater.run();
    }

    @JsonBody
    @GET("/json")
    public JsonObject getJson() {
        return new JsonObject().put("product", "Blåbærsyltetøy");
    }

    @GET("/setCookies")
    @ContentBody
    public String setCookies(
            @RequestParam("name") String name,
            @UnencryptedCookiePreview(value = "insecure", secure = false) Consumer<String> insecureCookie,
            @UnencryptedCookiePreview(value = "javascriptenabled", isHttpOnly = false) Consumer<String> notHttpOnlyCookie,
            @UnencryptedCookiePreview(value = "samesitenone", sameSite = UnencryptedCookiePreview.SameSite.None) Consumer<String> sameSiteNoneCookie,
            @UnencryptedCookiePreview(value = "samesitelax", sameSite = UnencryptedCookiePreview.SameSite.Lax) Consumer<String> sameSiteLaxCookie,
            @UnencryptedCookiePreview(value = "samesitestrict", sameSite = UnencryptedCookiePreview.SameSite.Lax) Consumer<String> sameSiteStrictCookie
    ) {
        insecureCookie.accept(name + " (not secure)");
        notHttpOnlyCookie.accept(name + " (not httpOnly)");
        sameSiteLaxCookie.accept(name + " (lax)");
        sameSiteNoneCookie.accept(name + " (samesite none)");
        sameSiteStrictCookie.accept(name + " (strict)");
        return "Cookies were set";
    }

    @GET("/cookies")
    @ContentBody
    public String getCookies(
            @HttpHeader("Origin") Optional<String> origin,
            @HttpHeader("Access-Control-Allow-Origin") Consumer<String> allowOrigin,
            @HttpHeader("Access-Control-Allow-Credentials") Consumer<String> allowCredentials,
            @UnencryptedCookiePreview(value = "insecure", secure = false) Optional<String> insecureCookie,
            @UnencryptedCookiePreview(value = "samesitenone", sameSite = UnencryptedCookiePreview.SameSite.None) Optional<String> sameSiteNoneCookie,
            @UnencryptedCookiePreview(value = "samesitelax", sameSite = UnencryptedCookiePreview.SameSite.Lax) Optional<String> sameSiteLaxCookie,
            @UnencryptedCookiePreview(value = "samesitestrict", sameSite = UnencryptedCookiePreview.SameSite.Lax) Optional<String> sameSiteStrictCookie
    ) {
        origin.ifPresentOrElse(allowOrigin, () -> allowOrigin.accept("https://example.com:31080"));
        allowCredentials.accept("true");
        return String.format("insecure: %s\nSame-Site=None: %s\nSame-Site=lax: %s\nSame-Site=strict: %s",
                insecureCookie, sameSiteNoneCookie, sameSiteLaxCookie, sameSiteStrictCookie);
    }
}

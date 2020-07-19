package org.actioncontroller;

import com.sun.net.httpserver.HttpServer;
import org.actioncontroller.client.ApiClientExchange;
import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.client.HttpURLConnectionApiClient;
import org.actioncontroller.httpserver.ApiHandler;
import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.servlet.ActionControllerConfigurationException;
import org.actioncontroller.servlet.ApiControllerActionRouter;
import org.actioncontroller.servlet.ApiServlet;
import org.actioncontroller.test.FakeApiClient;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.extend.junit.ExpectedLogEventsRule;
import org.slf4j.event.Level;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.ServletException;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AnnotationConfigurationTest {

    @Rule
    public ExpectedLogEventsRule expectedLogEvents = new ExpectedLogEventsRule(Level.WARN);

    @Retention(RetentionPolicy.RUNTIME)
    @HttpParameterMapping(MyEncryptedCookie.MapperFactory.class)
    @interface MyEncryptedCookie {
        String value();

        class MapperFactory implements HttpParameterMapperFactory<MyEncryptedCookie> {
            @Override
            public HttpParameterMapper create(MyEncryptedCookie annotation, Parameter parameter, ApiControllerContext context) throws Exception {
                SecretKeySpec keySpec= context.getAttribute(SecretKeySpec.class);
                Cipher encryptCipher = Cipher.getInstance("Blowfish");
                encryptCipher.init(Cipher.ENCRYPT_MODE, keySpec);
                Cipher decryptCipher = Cipher.getInstance("Blowfish");
                decryptCipher.init(Cipher.DECRYPT_MODE, keySpec);

                String name = annotation.value();
                if (parameter.getType() == Consumer.class) {
                    return exchange -> (Consumer<Object>) o -> exchange.setCookie(name, encrypt(encryptCipher, o.toString()), true, true);
                } else {
                    return exchange ->  exchange.getCookie(name)
                            .map(cookie -> decrypt(decryptCipher, cookie))
                            .map(string -> ApiHttpExchange.convertTo(string, name, parameter))
                            .orElseThrow();
                }
            }

            public static String encrypt(Cipher encryptCipher, String string) {
                try {
                    return Base64.getUrlEncoder().encodeToString(encryptCipher.doFinal(string.getBytes()));
                } catch (GeneralSecurityException e) {
                    throw new RuntimeException(e);
                }
            }

            public static String decrypt(Cipher decryptCipher, String value) {
                try {
                    return new String(decryptCipher.doFinal(Base64.getUrlDecoder().decode(value)));
                } catch (GeneralSecurityException e) {
                    throw new RuntimeException(e);
                }
            }


            @Override
            public HttpClientParameterMapper clientParameterMapper(MyEncryptedCookie annotation, Parameter parameter) {
                String name = annotation.value();
                if (parameter.getType() == Consumer.class) {
                    return consumer(parameter, exchange -> exchange.getResponseCookie(name));
                } else {
                    return (exchange, arg) -> exchange.addRequestCookie(name, arg);
                }
            }

            @SuppressWarnings({"unchecked", "rawtypes"})
            private HttpClientParameterMapper consumer(Parameter parameter, Function<ApiClientExchange, Optional<String>> f) {
                Type targetType = TypesUtil.typeParameter(parameter.getParameterizedType());
                return (exchange, arg) -> f.apply(exchange)
                        .ifPresent(string -> ((Consumer)arg).accept(ApiHttpExchange.convertParameterType(string, targetType)));
            }
        }
    }

    public static class Controller {

        @POST("/setCookie")
        @ContentBody
        public String setCookie(
                @RequestParam("cookieValue") String cookieValue,
                @MyEncryptedCookie("cookieName") Consumer<String> setCookieValue
        ) {
            setCookieValue.accept(cookieValue);
            return "ok";
        }

        @GET("/getCookie")
        @ContentBody
        public String getCookie(@MyEncryptedCookie("cookieName") String cookieValue) {
            return cookieValue;
        }
    }

    private String returnedCookieValue;

    @Test
    public void shouldUseConfigurationValue() throws MalformedURLException, ServletException, GeneralSecurityException {
        String encryptionKey = UUID.randomUUID().toString();
        SecretKeySpec keySpec = new SecretKeySpec(encryptionKey.getBytes(), "Blowfish");
        /*
        byte[] salt = "12345678".getBytes();
        int iterationCount = 40000;
        int keyLength = 128;
        String password = UUID.randomUUID().toString();
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
        PBEKeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, iterationCount, keyLength);
*/

        final URL contextRoot = new URL("http://example.com/test");
        final ApiServlet servlet = new ApiServlet(new Controller());
        servlet.getContext().setAttribute(keySpec);
        servlet.init(null);

        Controller client = ApiClientClassProxy.create(Controller.class, new FakeApiClient(contextRoot, "/api", servlet));

        client.setCookie("Hello", v -> returnedCookieValue = v);
        assertThat(decrypt(returnedCookieValue, keySpec)).isEqualTo("Hello");
        assertThat(client.getCookie(null)).isEqualTo("Hello");
    }

    private String decrypt(String value, SecretKeySpec keySpec) throws GeneralSecurityException {
        Cipher decryptCipher = Cipher.getInstance("Blowfish");
        decryptCipher.init(Cipher.DECRYPT_MODE, keySpec);
        return new String(decryptCipher.doFinal(Base64.getUrlDecoder().decode(value)));
    }

    @Test
    public void shouldThrowExceptionOnMissingRequiredConfigurationValue() {
        expectedLogEvents.expectMatch(event -> event.logger(ApiControllerActionRouter.class)
                        .pattern("Failed to setup {}")
                        .exception(ActionControllerConfigurationException.class)
                        .args("Controller.setCookie(String,Consumer)")
        );
        expectedLogEvents.expectMatch(event -> event.logger(ApiControllerActionRouter.class)
                        .pattern("Failed to setup {}")
                        .exception(ActionControllerConfigurationException.class)
                        .args("Controller.getCookie(String)")
        );

        final ApiServlet servlet = new ApiServlet(new Controller());

        assertThatThrownBy(() -> servlet.init(null))
                .isInstanceOf(ActionControllerConfigurationException.class)
                .hasMessageContaining("Missing context parameter, call servlet.getContext().setAttribute(\"" + SecretKeySpec.class.getName() + "\", ...)");
    }

    @Test
    public void shouldUseConfigurationValueWithJdkHttpServer() throws IOException, GeneralSecurityException {
        String encryptionKey = UUID.randomUUID().toString();
        SecretKeySpec keySpec = new SecretKeySpec(encryptionKey.getBytes(), "Blowfish");
        ApiControllerContext apiContext = new ApiControllerContext().setAttribute(keySpec);

        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/test/api", new ApiHandler(new Controller(), apiContext));
        server.start();

        String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/test/api";

        Controller client = ApiClientClassProxy.create(Controller.class, new HttpURLConnectionApiClient(baseUrl));
        client.setCookie("Hello world", v -> returnedCookieValue = v);
        assertThat(decrypt(returnedCookieValue, keySpec)).isEqualTo("Hello world");
    }

    @Test
    public void shouldThrowExceptionOnMissingRequiredConfigurationValueWithJdkHttpServer() {
        expectedLogEvents.expectMatch(event -> event.logger(ApiControllerActionRouter.class).formattedMessage("Failed to setup Controller.setCookie(String,Consumer)"));
        expectedLogEvents.expectMatch(event -> event.logger(ApiControllerActionRouter.class).formattedMessage("Failed to setup Controller.getCookie(String)"));

        assertThatThrownBy(() -> new ApiHandler(new Controller()))
                .isInstanceOf(ActionControllerConfigurationException.class)
                .hasMessageContaining("Missing context parameter, call servlet.getContext().setAttribute(\"" + SecretKeySpec.class.getName() + "\", ...)");
    }


}

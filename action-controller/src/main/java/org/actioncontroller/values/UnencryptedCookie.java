package org.actioncontroller.values;

import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.exceptions.HttpRequestException;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpParameterMapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.util.Objects;

import static org.actioncontroller.ApiHttpExchange.convertRequestValue;

/**
 * Maps the parameter to the specified HTTP request cookie, URL decoding and converting the type if necessary.
 * If the session parameter is missing, aborts the request with 401 Unauthorized, unless the parameter type is Optional.
 * If the parameter type is Consumer, calling parameter.accept() sets the cookie value instead of returning it
 * converting the type and URL encoding.
 *
 * @see HttpParameterMapping
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@HttpParameterMapping(UnencryptedCookie.Factory.class)
public @interface UnencryptedCookie {

    /**
     * The name of the cookie
     */
    String value();

    /**
     * If false, omits the Secure flag on the cookie, meaning that the cookie will be sent over HTTP as well as HTTPS.
     * For testing purposes, action-controller always omits the Secure flag for localhost addresses
     */
    boolean secure() default true;

    /**
     * If false, omits the HttpOnly flag on the cookie, meaning that the cookie can be read by client-side JavaScript.
     * You normally want HttpOnly to mitigate the impact of Cross-site Scripting attacks
     */
    boolean isHttpOnly() default true;

    class Factory implements HttpParameterMapperFactory<UnencryptedCookie> {

        @Override
        public HttpParameterMapper create(UnencryptedCookie annotation, Parameter parameter, ApiControllerContext context) {
            String name = annotation.value();
            return HttpParameterMapperFactory.createMapper(
                    parameter.getParameterizedType(),
                    (exchange, type) -> exchange.getCookie(name).map(value -> convertRequestValue(value, type)),
                    (exchange, o) -> exchange.setCookie(name, Objects.toString(o, null), annotation.secure(), annotation.isHttpOnly()),
                    () -> { throw new HttpRequestException("Missing cookie " + name); }
            );
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(UnencryptedCookie annotation, Parameter parameter) {
            String name = annotation.value();
            return HttpParameterMapperFactory.createClientMapper(
                    parameter.getParameterizedType(),
                    (exchange, arg) -> {
                        if (arg != null) exchange.addRequestCookie(name, arg);
                    },
                    (exchange, targetType) -> exchange.getResponseCookie(name)
                                        .map(string -> convertRequestValue(string, targetType))
            );
        }
    }
}

package org.actioncontroller;

import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpParameterMapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.function.Consumer;

import static org.actioncontroller.meta.ApiHttpExchange.convertParameterType;

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
            if (parameter.getType() == Consumer.class) {
                return exchange -> (Consumer<Object>) o -> exchange.setCookie(name, Objects.toString(o, null), annotation.secure(), annotation.isHttpOnly());
            } else {
                return exchange -> ApiHttpExchange.convertTo(exchange.getCookie(name), name, parameter);
            }
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(UnencryptedCookie annotation, Parameter parameter) {
            String name = annotation.value();
            if (parameter.getType() == Consumer.class) {
                Type targetType = TypesUtil.typeParameter(parameter.getParameterizedType());
                return (exchange, arg) -> {
                    if (arg != null) {
                        ((Consumer) arg).accept(convertParameterType(exchange.getResponseCookie(name), targetType));
                    }
                };
            } else {
                return (exchange, arg) -> {
                    if (arg != null) {
                        exchange.addRequestCookie(name, arg);
                    }
                };
            }
        }
    }
}

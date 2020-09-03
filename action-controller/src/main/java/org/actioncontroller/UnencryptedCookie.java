package org.actioncontroller;

import org.actioncontroller.client.ApiClientExchange;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
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
            boolean optional = parameter.getType() == Optional.class;
            Type type = optional ? TypesUtil.typeParameter(parameter.getParameterizedType()) : parameter.getParameterizedType();

            String name = annotation.value();
            if (parameter.getType() == Consumer.class) {
                return exchange -> (Consumer<?>) o -> exchange.setCookie(name, Objects.toString(o, null), annotation.secure(), annotation.isHttpOnly());
            } else if (parameter.getType() == AtomicReference.class) {
                return new AtomicReferenceMapper(name, parameter, annotation);
            } else if (optional) {
                return exchange -> getCookie(exchange, name, type);
            } else {
                return exchange -> getCookie(exchange, name, type)
                        .orElseThrow(() -> new HttpRequestException("Missing cookie " + name));
            }
        }

        protected Optional<Object> getCookie(ApiHttpExchange exchange, String name, Type type) {
            return exchange.getCookie(name).map(value -> convertParameterType(value, type));
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(UnencryptedCookie annotation, Parameter parameter) {
            String name = annotation.value();
            if (parameter.getType() == Consumer.class) {
                Type targetType = TypesUtil.typeParameter(parameter.getParameterizedType());
                return (exchange, arg) -> {
                    if (arg != null) {
                        getResponseCookie(name, targetType, exchange)
                                .ifPresent(o -> ((Consumer)arg).accept(o));
                    }
                };
            } else if (parameter.getType() == AtomicReference.class) {
                Type targetType = TypesUtil.typeParameter(parameter.getParameterizedType());
                return (exchange, arg) -> {
                    if (arg != null) {
                        exchange.addRequestCookie(name, arg);
                        getResponseCookie(name, targetType, exchange)
                                .ifPresent(o -> ((AtomicReference)arg).set(o));
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

        public Optional<Object> getResponseCookie(String name, Type targetType, ApiClientExchange exchange) {
            return exchange.getResponseCookie(name)
                    .map(string -> convertParameterType(string, targetType));
        }

        private class AtomicReferenceMapper implements HttpParameterMapper {
            private final String name;
            private final UnencryptedCookie annotation;
            private final Class<?> parameterType;

            public AtomicReferenceMapper(String name, Parameter parameter, UnencryptedCookie annotation) {
                this.name = name;
                this.annotation = annotation;
                parameterType = TypesUtil.typeParameter(parameter.getParameterizedType());
            }

            @Override
            public Object apply(ApiHttpExchange exchange) {
                return new AtomicReference<>(Factory.this.getCookie(exchange, name, parameterType).orElse(null));
            }

            @Override
            public void onComplete(ApiHttpExchange exchange, Object argument) {
                AtomicReference<?> arg = (AtomicReference<?>) argument;
                exchange.setCookie(name, Objects.toString(arg.get(), null), annotation.secure(), annotation.isHttpOnly());
            }
        }
    }
}

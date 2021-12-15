package org.actioncontroller.values;

import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.ApiHttpExchange;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.util.TypesUtil;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
            Class<?> type = TypesUtil.getRawType(parameter.getParameterizedType());
            if (type == Consumer.class) {
                return exchange -> (Consumer<?>) o1 -> exchange.setCookie(name, Objects.toString(o1, null), annotation.secure(), annotation.isHttpOnly());
            } else if (type == AtomicReference.class) {
                TypeConverter converter = TypeConverterFactory.fromStrings(parameter.getParameterizedType(), "cookie " + name);
                return new HttpParameterMapper() {
                    @Override
                    public Object apply(ApiHttpExchange exchange) {
                        return converter.apply(exchange.getCookies(name));
                    }

                    @Override
                    public void onComplete(ApiHttpExchange exchange, Object argument) {
                        Object o = ((AtomicReference<?>) argument).get();
                        exchange.setCookie(name, Objects.toString(o, null), annotation.secure(), annotation.isHttpOnly());
                    }
                };
            } else {
                TypeConverter converter = TypeConverterFactory.fromStrings(parameter.getParameterizedType(), "cookie " + name);
                return exchange -> converter.apply(exchange.getCookies(name));
            }
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(UnencryptedCookie annotation, Parameter parameter) {
            String name = annotation.value();
            Type parameterType = parameter.getParameterizedType();
            if (parameter.getType() == Consumer.class) {
                TypeConverter converter = TypeConverterFactory.fromStrings(TypesUtil.typeParameter(parameterType), "cookie " + annotation.value());
                return (exchange, arg) -> {
                    if (arg != null) {
                        ((Consumer) arg).accept(converter.apply(exchange.getResponseCookies(name)));
                    }
                };
            } else if (parameter.getType() == AtomicReference.class) {
                TypeConverter converter = TypeConverterFactory.fromStrings(parameterType, "cookie " + annotation.value());
                Type typeParameter = TypesUtil.typeParameter(parameterType);
                return (exchange, atomicReference) -> {
                    if (atomicReference != null) {
                        Object arg = ((AtomicReference<?>) atomicReference).get();
                        if (arg != null) exchange.addRequestCookie(name, arg);
                        AtomicReference value = (AtomicReference) converter.apply(exchange.getResponseCookies(name));
                        ((AtomicReference) atomicReference).set(value.get());
                    }
                };
            } else {
                return (exchange, arg) -> {
                    if (arg != null) exchange.addRequestCookie(name, arg);
                };
            }
        }
    }
}

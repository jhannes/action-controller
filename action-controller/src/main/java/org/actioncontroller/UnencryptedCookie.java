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

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@HttpParameterMapping(UnencryptedCookie.Factory.class)
public @interface UnencryptedCookie {

    String value();

    boolean secure() default true;

    class Factory implements HttpParameterMapperFactory<UnencryptedCookie> {

        @Override
        public HttpParameterMapper create(UnencryptedCookie annotation, Parameter parameter, ApiControllerContext context) {
            String name = annotation.value();
            if (parameter.getType() == Consumer.class) {
                return exchange -> (Consumer<Object>) o -> exchange.setCookie(name, Objects.toString(o, null), annotation.secure());
            } else {
                return exchange -> ApiHttpExchange.convertTo(exchange.getCookie(name), name, parameter);
            }
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(UnencryptedCookie annotation, Parameter parameter) {
            String name = annotation.value();
            if (parameter.getType() == Consumer.class) {
                Type targetType = ApiHttpExchange.getConsumerType(parameter);
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

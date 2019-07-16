package org.actioncontroller;

import org.actioncontroller.meta.HttpClientParameterMapping;
import org.actioncontroller.meta.HttpClientParameterMapperFactory;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.util.Optional;
import java.util.function.Consumer;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@HttpParameterMapping(UnencryptedCookie.Factory.class)
@HttpClientParameterMapping(UnencryptedCookie.Factory.class)
public @interface UnencryptedCookie {

    String value();

    boolean secure() default true;

    public class Factory implements HttpParameterMapperFactory<UnencryptedCookie>, HttpClientParameterMapperFactory<UnencryptedCookie> {

        @Override
        public HttpParameterMapper create(UnencryptedCookie annotation, Parameter parameter) {
            String name = annotation.value();
            if (parameter.getType() == Consumer.class) {
                return exchange -> (Consumer<Object>) o -> exchange.setCookie(name, o.toString(), annotation.secure());
            } else {
                return exchange -> exchange.getCookie(name, parameter);
            }
        }

        @Override
        public HttpClientParameterMapper createClient(UnencryptedCookie annotation, Parameter parameter) {
            String name = annotation.value();
            if (parameter.getType() == Optional.class) {
                return (exchange, arg) ->
                    ((Optional)arg).ifPresent(a -> exchange.addRequestCookie(name, a.toString()));
            } else {
                return (exchange, arg) ->
                    exchange.addRequestCookie(name, arg.toString());
            }
        }
    }
}

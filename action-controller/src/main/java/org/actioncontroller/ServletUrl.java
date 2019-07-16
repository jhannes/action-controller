package org.actioncontroller;

import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.net.URL;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@HttpParameterMapping(ServletUrl.MapperFactory.class)
public @interface ServletUrl {
    class MapperFactory implements HttpParameterMapperFactory<ServletUrl> {
        @Override
        public HttpParameterMapper create(ServletUrl annotation, Parameter parameter) {
            if (parameter.getType() == URL.class) {
                return ApiHttpExchange::getApiURL;
            } else if (parameter.getType() == String.class) {
                return exchange -> exchange.getApiURL().toString();
            }
            throw new IllegalArgumentException("Can't map " + parameter);
        }
    }
}

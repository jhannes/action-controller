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

/**
 * Extract the context URL into the parameter as URL or String
 *
 * @see HttpParameterMapping
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@HttpParameterMapping(ContextUrl.MapperFactory.class)
public @interface ContextUrl {

    class MapperFactory implements HttpParameterMapperFactory<ContextUrl> {
        @Override
        public HttpParameterMapper create(ContextUrl annotation, Parameter parameter, ApiControllerContext context) {
            if (parameter.getType() == URL.class) {
                return ApiHttpExchange::getContextURL;
            } else if (parameter.getType() == String.class) {
                return exchange -> exchange.getContextURL().toString();
            }
            throw new IllegalArgumentException("Can't map " + parameter);
        }
    }
}

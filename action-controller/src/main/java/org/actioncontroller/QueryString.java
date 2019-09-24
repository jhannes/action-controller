package org.actioncontroller;

import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpParameterMapping;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Parameter;

@Retention(RetentionPolicy.RUNTIME)
@HttpParameterMapping(QueryString.ParameterMapperFactory.class)
public @interface QueryString {
    class ParameterMapperFactory implements HttpParameterMapperFactory<QueryString> {
        @Override
        public HttpParameterMapper create(QueryString annotation, Parameter parameter, ApiControllerContext context) {
            return ApiHttpExchange::getQueryString;
        }
    }
}

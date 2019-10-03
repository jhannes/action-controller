package org.actioncontroller;

import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpClientReturnMapper;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpReturnMapper;
import org.actioncontroller.meta.HttpReturnMapperFactory;
import org.actioncontroller.meta.HttpReturnMapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;

/**
 * When used on a parameter, maps the HTTP Request header to the parameter, converting the type if necessary.
 * When used on method, maps the return value to the specified HTTP Response header
 *
 * @see HttpReturnMapping
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.PARAMETER})
@HttpParameterMapping(HttpHeader.Mapper.class)
@HttpReturnMapping(HttpHeader.Mapper.class)
public @interface HttpHeader {
    String value();

    class Mapper implements
            HttpParameterMapperFactory<HttpHeader>,
            HttpReturnMapperFactory<HttpHeader>
    {
        @Override
        public HttpParameterMapper create(HttpHeader annotation, Parameter parameter, ApiControllerContext context) {
            String name = annotation.value();
            Class<?> type = parameter.getType();
            return exchange -> ApiHttpExchange.convertParameterType(exchange.getHeader(name), type);
        }

        @Override
        public HttpReturnMapper create(HttpHeader annotation, Class<?> returnType) {
            return (result, exchange) -> exchange.setResponseHeader(annotation.value(), String.valueOf(result));
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(HttpHeader annotation, Parameter parameter) {
            return (exchange, arg) -> exchange.setHeader(annotation.value(), arg);
        }

        @Override
        public HttpClientReturnMapper createClientMapper(HttpHeader annotation, Class<?> returnType) {
            return (exchange -> exchange.getResponseHeader(annotation.value()));
        }
    }

}

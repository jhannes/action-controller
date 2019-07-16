package org.actioncontroller;

import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpClientParameterMapperFactory;
import org.actioncontroller.meta.HttpClientParameterMapping;
import org.actioncontroller.meta.HttpClientReturnMapper;
import org.actioncontroller.meta.HttpClientReturnMapperFactory;
import org.actioncontroller.meta.HttpClientReturnMapping;
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

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.PARAMETER})
@HttpParameterMapping(HttpHeader.Mapper.class)
@HttpReturnMapping(HttpHeader.Mapper.class)
@HttpClientReturnMapping(HttpHeader.Mapper.class)
@HttpClientParameterMapping(HttpHeader.Mapper.class)
public @interface HttpHeader {
    String value();

    public class Mapper implements
            HttpParameterMapperFactory<HttpHeader>,
            HttpReturnMapperFactory<HttpHeader>,
            HttpClientParameterMapperFactory<HttpHeader>,
            HttpClientReturnMapperFactory<HttpHeader>
    {
        @Override
        public HttpParameterMapper create(HttpHeader annotation, Parameter parameter) {
            String name = annotation.value();
            Class<?> type = parameter.getType();
            return exchange -> ApiHttpExchange.convertParameterType(exchange.getHeader(name), type);
        }

        @Override
        public HttpReturnMapper create(HttpHeader annotation, Class<?> returnType) {
            return (result, exchange) -> exchange.setResponseHeader(annotation.value(), String.valueOf(result));
        }

        @Override
        public HttpClientParameterMapper createClient(HttpHeader annotation, Parameter parameter) {
            return (exchange, arg) -> exchange.setHeader(annotation.value(), arg);
        }

        @Override
        public HttpClientReturnMapper createClient(HttpHeader annotation, Class<?> returnType) {
            return (exchange -> exchange.getResponseHeader(annotation.value()));
        }
    }

}

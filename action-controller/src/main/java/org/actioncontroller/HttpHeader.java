package org.actioncontroller;

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
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Optional;

import static org.actioncontroller.meta.ApiHttpExchange.convertParameterType;

/**
 * When used on a parameter, maps the HTTP Request header to the parameter, converting the type if necessary.
 * When used on method, maps the return value to the specified HTTP Response header.
 * If the parameter type is Consumer, calling parameter.accept() sets the http header value instead of returning it.
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
            return HttpParameterMapperFactory.createMapper(
                    parameter.getParameterizedType(),
                    (exchange, type) -> exchange.getHeader(name).map(header -> convertParameterType(header, type)),
                    (exchange, o) -> exchange.setResponseHeader(annotation.value(), Objects.toString(o, null)),
                    () -> { throw new HttpRequestException("Missing required header " + name); }
            );
        }

        @Override
        public HttpReturnMapper create(HttpHeader annotation, Type returnType) {
            return (result, exchange) -> exchange.setResponseHeader(annotation.value(), Objects.toString(result, null));
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(HttpHeader annotation, Parameter parameter) {
            return HttpParameterMapperFactory.createClientMapper(
                    parameter.getParameterizedType(),
                    (exchange, o) -> exchange.setHeader(annotation.value(), o),
                    (exchange, type) -> Optional.ofNullable(exchange.getResponseHeader(annotation.value()))
                        .map(value -> convertParameterType(value, type))
            );
        }

        @Override
        public HttpClientReturnMapper createClientMapper(HttpHeader annotation, Type returnType) {
            return (exchange -> exchange.getResponseHeader(annotation.value()));
        }
    }

}

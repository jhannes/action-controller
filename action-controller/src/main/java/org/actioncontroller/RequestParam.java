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

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@HttpParameterMapping(RequestParam.ParameterMapperFactory.class)
public @interface RequestParam {

    String value();

    @Retention(RUNTIME)
    @Target(PARAMETER)
    @HttpParameterMapping(ClientIpParameterMapperFactory.class)
    @interface ClientIp {
    }

    class ClientIpParameterMapperFactory implements HttpParameterMapperFactory<ClientIp> {

        @Override
        public HttpParameterMapper create(ClientIp annotation, Parameter parameter) {
            return ApiHttpExchange::getClientIp;
        }
    }

    class ParameterMapperFactory implements HttpParameterMapperFactory<RequestParam> {
        @Override
        public HttpParameterMapper create(RequestParam annotation, Parameter parameter) {
            String name = annotation.value();
            return (exchange) -> exchange.getParameter(name, parameter);
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(RequestParam annotation, Parameter parameter) {
            String name = annotation.value();
            return (exchange, arg) -> exchange.setRequestParameter(name, arg);
        }
    }
}

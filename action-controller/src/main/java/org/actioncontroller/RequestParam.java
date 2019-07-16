package org.actioncontroller;

import org.actioncontroller.meta.ApiHttpExchange;
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

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@HttpParameterMapping(RequestParam.ParameterMapperFactory.class)
@HttpClientParameterMapping(RequestParam.ParameterMapperFactory.class)
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

    class ParameterMapperFactory implements
            HttpParameterMapperFactory<RequestParam>,
            HttpClientParameterMapperFactory<RequestParam>
    {
        @Override
        public HttpParameterMapper create(RequestParam annotation, Parameter parameter) {
            String name = annotation.value();
            return (exchange) -> exchange.getParameter(name, parameter);
        }

        @Override
        public HttpClientParameterMapper createClient(RequestParam annotation, Parameter parameter) {
            String name = annotation.value();
            if (parameter.getType() == Optional.class) {
                return (exchange, arg) ->
                        ((Optional)arg).ifPresent(a -> exchange.setRequestParameter(name, a.toString()));
            } else {
                return (exchange, arg) -> exchange.setRequestParameter(name, arg.toString());
            }
        }
    }
}

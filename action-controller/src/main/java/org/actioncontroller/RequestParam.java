package org.actioncontroller;

import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.meta.HttpClientParameterMapping;
import org.actioncontroller.meta.HttpClientParameterMapperFactory;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpRequestParameterMapping;
import org.actioncontroller.meta.HttpRequestParameterMappingFactory;

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
@HttpParameterMapping(RequestParam.RequestParameterMappingFactory.class)
@HttpClientParameterMapping(RequestParam.RequestParameterMappingFactory.class)
public @interface RequestParam {

    String value();

    @Retention(RUNTIME)
    @Target(PARAMETER)
    @HttpParameterMapping(RequestParam.ClientIpParameterMappingFactory.class)
    @interface ClientIp {
    }

    class ClientIpParameterMappingFactory implements HttpRequestParameterMappingFactory<ClientIp> {

        @Override
        public HttpRequestParameterMapping create(ClientIp annotation, Parameter parameter) {
            return ApiHttpExchange::getClientIp;
        }
    }

    class RequestParameterMappingFactory implements
            HttpRequestParameterMappingFactory<RequestParam>,
            HttpClientParameterMapperFactory<RequestParam>
    {
        @Override
        public HttpRequestParameterMapping create(RequestParam annotation, Parameter parameter) {
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

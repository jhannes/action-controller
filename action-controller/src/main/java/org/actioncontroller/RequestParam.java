package org.actioncontroller;

import org.actioncontroller.meta.AbstractHttpRequestParameterMapping;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpRequestParameterMapping;
import org.actioncontroller.meta.HttpRequestParameterMappingFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@HttpParameterMapping(RequestParam.RequestParameterMappingFactory.class)
public @interface RequestParam {

    String value();

    @Retention(RUNTIME)
    @Target(PARAMETER)
    @HttpParameterMapping(RequestParam.ClientIpParameterMappingFactory.class)
    @interface ClientIp {
    }

    public class ClientIpParameterMappingFactory implements HttpRequestParameterMappingFactory<ClientIp> {

        @Override
        public HttpRequestParameterMapping create(ClientIp annotation, Parameter parameter) {
            return (req, pathParameter) -> req.getRemoteAddr();
        }
    }

    public class RequestParameterMappingFactory implements HttpRequestParameterMappingFactory<RequestParam> {
        @Override
        public HttpRequestParameterMapping create(RequestParam annotation, Parameter parameter) {
            String name = annotation.value();
            return (req, pathParams) -> AbstractHttpRequestParameterMapping.convertTo(
                    req.getParameter(name),
                    name,
                    parameter
            );
        }
    }
}

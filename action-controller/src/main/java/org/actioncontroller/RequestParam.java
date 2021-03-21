package org.actioncontroller;

import org.actioncontroller.client.ApiClientExchange;
import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.servlet.ServletHttpExchange;
import org.actioncontroller.test.FakeApiClient;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Maps the HTTP request parameter to the parameter, converting the type if necessary.
 *
 * @see HttpParameterMapping
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@HttpParameterMapping(RequestParam.MapperFactory.class)
public @interface RequestParam {

    String value();

    /**
     * Maps the HTTP client IP to the parameter as String. Resolves X-Forwarded-For proxy headers
     */
    @Retention(RUNTIME)
    @Target(PARAMETER)
    @HttpParameterMapping(ClientIpParameterMapperFactory.class)
    @interface ClientIp {
    }

    /**
     * NOT SUPPORTED ON JdkHttpExchange. Returns the remote user for the request as set on the servlet container.
     */
    @Retention(RUNTIME)
    @Target(PARAMETER)
    @HttpParameterMapping(RemoteUserParameterMapperFactory.class)
    @interface RemoteUser {
    }

    class ClientIpParameterMapperFactory implements HttpParameterMapperFactory<ClientIp> {

        @Override
        public HttpParameterMapper create(ClientIp annotation, Parameter parameter, ApiControllerContext context) {
            return ApiHttpExchange::getClientIp;
        }
    }

    class MapperFactory implements HttpParameterMapperFactory<RequestParam> {
        @Override
        public HttpParameterMapper create(RequestParam annotation, Parameter parameter, ApiControllerContext context) {
            String name = annotation.value();
            return (exchange) -> convertTo(exchange.getParameters(name), name, parameter.getParameterizedType());
        }

        static Object convertTo(List<String> value, String parameterName, Type type) {
            try {
                boolean optional = TypesUtil.getRawType(type) == Optional.class;
                if (value == null || value.isEmpty()) {
                    if (!optional) {
                        throw new HttpRequestException("Missing required parameter " + parameterName);
                    }
                    return Optional.empty();
                } else if (optional) {
                    return Optional.of(convertTo(value, parameterName, TypesUtil.typeParameter(type)));
                } else if (TypesUtil.isCollectionType(type)) {
                    List<Object> result = new ArrayList<>();
                    for (String s : value) {
                        result.add(ApiHttpExchange.convertRequestValue(s, TypesUtil.typeParameter(type)));
                    }
                    return result;
                } else {
                    return ApiHttpExchange.convertRequestValue(value.get(0), type);
                }
            } catch (IllegalArgumentException e) {
                throw new HttpRequestException("Could not convert " + parameterName + "=" + value + " to " + type.getTypeName());
            }
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(RequestParam annotation, Parameter parameter) {
            String name = annotation.value();
            return (exchange, arg) -> exchange.setRequestParameter(name, arg);
        }
    }

    class RemoteUserParameterMapperFactory implements HttpParameterMapperFactory<RemoteUser> {
        @Override
        public HttpParameterMapper create(RemoteUser annotation, Parameter parameter, ApiControllerContext context) {
            return ApiHttpExchange
                    .withOptional(parameter, exchange -> exchange.getUserPrincipal() != null ? exchange.getUserPrincipal().getName() : null);
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(RemoteUser annotation, Parameter parameter) {
            return ApiClientExchange.withOptional(parameter, (exchange, object) -> {
                if (exchange instanceof FakeApiClient.FakeApiClientExchange && object != null) {
                    ((FakeApiClient.FakeApiClientExchange)exchange).setRemoteUser(object);
                }
            });
        }
    }

}

package org.actioncontroller.values;

import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.TypeConverter;
import org.actioncontroller.TypeConverterFactory;
import org.actioncontroller.ApiHttpExchange;
import org.actioncontroller.exceptions.HttpRequestException;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.test.FakeApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.security.Principal;
import java.util.Optional;
import java.util.function.Function;

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

    Logger logger = LoggerFactory.getLogger(RequestParam.class);

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
            TypeConverter converter = TypeConverterFactory.fromStrings(parameter.getParameterizedType(), "parameter " + name);
            return (exchange) -> converter.apply(exchange.getParameters(name));
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
            if (parameter.getType() == Optional.class) {
                return exchange -> exchange.getUserPrincipal() != null ? Optional.of(exchange.getUserPrincipal().getName()) : Optional.empty();
            } else {
                return exchange -> Optional.ofNullable(exchange.getUserPrincipal())
                        .map(Principal::getName)
                        .orElseThrow(() -> new HttpRequestException("Missing required parameter value"));
            }
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(RemoteUser annotation, Parameter parameter) {
            if (parameter.getType() == Optional.class) {
                return (exchange, arg) -> {
                    if (exchange instanceof FakeApiClient.FakeApiClientExchange) {
                        FakeApiClient.FakeApiClientExchange clientExchange = (FakeApiClient.FakeApiClientExchange) exchange;
                        Optional.ofNullable((Optional<?>) arg).flatMap(Function.identity())
                                .ifPresent(clientExchange::setRemoteUser);
                    }
                };
            } else {
                return (exchange, object) -> {
                    if (exchange instanceof FakeApiClient.FakeApiClientExchange && object != null) {
                        ((FakeApiClient.FakeApiClientExchange) exchange).setRemoteUser(object);
                    }
                };
            }
        }
    }

}

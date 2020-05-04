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

    /**
     * NOT SUPPORTED ON JdkHttpExchange. Returns the remote principal for the request as set on the servlet container.
     */
    @Retention(RUNTIME)
    @Target(PARAMETER)
    @HttpParameterMapping(PrincipalParameterMapperFactory.class)
    @interface Principal {
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
            return (exchange) -> exchange.getParameter(name, parameter);
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
                    .withOptional(parameter, exchange -> ((ServletHttpExchange) exchange).getRequest().getRemoteUser());
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(RemoteUser annotation, Parameter parameter) {
            return ApiClientExchange.withOptional(parameter, (exchange, object) -> {
                if (exchange instanceof FakeApiClient.FakeApiClientExchange) {
                    ((FakeApiClient.FakeApiClientExchange)exchange).setRemoteUser(object);
                }
            });
        }
    }

    class PrincipalParameterMapperFactory implements HttpParameterMapperFactory<Principal> {
        @Override
        public HttpParameterMapper create(Principal annotation, Parameter parameter, ApiControllerContext context) {
            if (parameter.getType() == Optional.class) {
                Class<?> targetType = TypesUtil.typeParameter(parameter.getParameterizedType());
                return exchange -> {
                    java.security.Principal principal = exchange.getUserPrincipal();
                    if (principal == null) {
                        return Optional.empty();
                    } else if (targetType.isAssignableFrom(principal.getClass())) {
                        return Optional.of(principal);
                    } else {
                        throw new HttpUnauthorizedException("Login required");
                    }
                };
            } else {
                return exchange -> {
                    java.security.Principal principal = exchange.getUserPrincipal();
                    if (principal != null && parameter.getType().isAssignableFrom(principal.getClass())) {
                        return principal;
                    } else {
                        throw new HttpUnauthorizedException("Login required");
                    }
                };
            }
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(Principal annotation, Parameter parameter) {
            return ApiClientExchange.withOptional(parameter, (exchange, object) -> {
                if (exchange instanceof FakeApiClient.FakeApiClientExchange) {
                    ((FakeApiClient.FakeApiClientExchange)exchange).setRemoteUser(object);
                }
            });
        }
    }
}

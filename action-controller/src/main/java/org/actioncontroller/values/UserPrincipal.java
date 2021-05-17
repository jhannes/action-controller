package org.actioncontroller.values;

import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.exceptions.HttpForbiddenException;
import org.actioncontroller.exceptions.HttpUnauthorizedException;
import org.actioncontroller.util.TypesUtil;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.test.FakeApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.Function;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Returns the remote principal for the request as set on the servlet container.
 */
@Retention(RUNTIME)
@Target(PARAMETER)
@HttpParameterMapping(UserPrincipal.MapperFactory.class)
public @interface UserPrincipal {

    class MapperFactory implements HttpParameterMapperFactory<UserPrincipal> {
        private static final Logger logger = LoggerFactory.getLogger(MapperFactory.class);

        @Override
        public HttpParameterMapper create(UserPrincipal annotation, Parameter parameter, ApiControllerContext context) {
            if (parameter.getType() == Optional.class) {
                Type targetType = TypesUtil.typeParameter(parameter.getParameterizedType());
                return exchange -> {
                    java.security.Principal principal = exchange.getUserPrincipal();
                    if (principal == null) {
                        return Optional.empty();
                    } else if (TypesUtil.isInstanceOf(targetType, principal.getClass())) {
                        return Optional.of(principal);
                    } else {
                        logger.info("Can't assign {} to {}", principal, parameter.getType());
                        return Optional.empty();
                    }
                };
            } else {
                return exchange -> {
                    java.security.Principal principal = exchange.getUserPrincipal();
                    if (principal == null) {
                        throw new HttpUnauthorizedException();
                    } else if (parameter.getType().isAssignableFrom(principal.getClass())) {
                        return principal;
                    } else {
                        logger.info("Can't assign {} to {}", principal, parameter.getType());
                        throw new HttpForbiddenException();
                    }
                };
            }
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(UserPrincipal annotation, Parameter parameter) {
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
                        ((FakeApiClient.FakeApiClientExchange)exchange).setRemoteUser(object);
                    }
                };
            }
        }
    }
}

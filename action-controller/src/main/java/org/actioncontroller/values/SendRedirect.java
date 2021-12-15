package org.actioncontroller.values;

import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.exceptions.ActionControllerConfigurationException;
import org.actioncontroller.meta.HttpClientReturnMapper;
import org.actioncontroller.meta.HttpReturnMapper;
import org.actioncontroller.meta.HttpReturnMapperFactory;
import org.actioncontroller.meta.HttpReturnMapping;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Type;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Maps the method response value as an HTTP 302 redirect, supporting Strings and URLs. URLs not starting
 * with "http(s)" are interpreted as relative paths to server root if they start with "/" or api URL otherwise
 *
 * @see HttpReturnMapping
 */
@Retention(RUNTIME)
@Target(METHOD)
@HttpReturnMapping(SendRedirect.MappingFactory.class)
public @interface SendRedirect {

    String value() default "";

    class MappingFactory implements HttpReturnMapperFactory<SendRedirect> {
        @Override
        public HttpReturnMapper create(SendRedirect annotation, Type returnType, ApiControllerContext context) {
            if (!annotation.value().isEmpty()) {
                if (returnType != Void.TYPE) {
                    throw new ActionControllerConfigurationException("When using " + SendRedirect.class.getName() + " with value(), return value must be void");
                }
                return (result, exchange) -> exchange.sendRedirect(annotation.value());
            }
            return (result, exchange) -> {
                String path = result.toString();
                if (path.matches("^https?://.*")) {
                    exchange.sendRedirect(path);
                } else if (path.startsWith("/")) {
                    exchange.sendRedirect(exchange.getServerURL() + path);
                } else {
                    exchange.sendRedirect(exchange.getApiURL() + "/" + path);
                }
            };
        }

        @Override
        public HttpClientReturnMapper createClientMapper(SendRedirect annotation, Type returnType) {
            TypeConverter converter = TypeConverterFactory.fromStrings(returnType, "header Location");
            return exchange -> {
                if (exchange.getResponseCode() < 300) {
                    throw new IllegalArgumentException("Expected redirect, but was " + exchange.getResponseCode());
                }
                return converter.apply(exchange.getResponseHeaders("Location"));
            };
        }
    }
}

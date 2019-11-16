package org.actioncontroller;

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

    class MappingFactory implements HttpReturnMapperFactory<SendRedirect> {
        @Override
        public HttpReturnMapper create(SendRedirect annotation, Class<?> returnType) {
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
            return exchange -> {
                if (exchange.getResponseCode() < 300) {
                    throw new IllegalArgumentException("Expected redirect, but was " + exchange.getResponseCode());
                }
                return exchange.getResponseHeader("Location");
            };
        }
    }
}

package org.actioncontroller;

import org.actioncontroller.meta.HttpClientReturnMapper;
import org.actioncontroller.meta.HttpReturnMapperFactory;
import org.actioncontroller.meta.HttpReturnMapping;
import org.actioncontroller.meta.HttpReturnMapper;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

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
        public HttpClientReturnMapper createClient(SendRedirect annotation, Class<?> returnType) {
            return exchange -> {
                if (exchange.getResponseCode() < 300) {
                    throw new IllegalArgumentException("Expected redirect, but was " + exchange.getResponseCode());
                }
                return exchange.getResponseHeader("Location");
            };
        }
    }
}

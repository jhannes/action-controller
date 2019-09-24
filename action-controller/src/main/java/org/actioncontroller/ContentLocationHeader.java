package org.actioncontroller;

import org.actioncontroller.meta.HttpClientReturnMapper;
import org.actioncontroller.meta.HttpReturnMapperFactory;
import org.actioncontroller.meta.HttpReturnMapping;
import org.actioncontroller.meta.HttpReturnMapper;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.net.URL;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target(METHOD)
@HttpReturnMapping(ContentLocationHeader.MappingFactory.class)
public @interface ContentLocationHeader {

    String FIELD_NAME = "Content-location";

    class MappingFactory implements HttpReturnMapperFactory<ContentLocationHeader> {
        @Override
        public HttpReturnMapper create(ContentLocationHeader annotation, Class<?> returnType) {
            if (returnType == URL.class) {
                return (result, exchange) -> exchange.setResponseHeader(FIELD_NAME, result.toString());
            }
            return (result, exchange) ->
                    exchange.setResponseHeader(FIELD_NAME, exchange.getApiURL() + result.toString());
        }

        @Override
        public HttpClientReturnMapper createClientMapper(ContentLocationHeader annotation, Class<?> returnType) {
            return exchange -> exchange.getResponseHeader(ContentLocationHeader.FIELD_NAME);
        }
    }
}


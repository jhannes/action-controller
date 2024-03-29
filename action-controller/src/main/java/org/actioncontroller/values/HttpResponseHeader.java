package org.actioncontroller.values;

import org.actioncontroller.ApiControllerContext;
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
 * Maps the return value to the specified HTTP Response header
 *
 * @see HttpReturnMapping
 */
@Retention(RUNTIME)
@Target(METHOD)
@HttpReturnMapping(HttpResponseHeader.MappingFactory.class)
public @interface HttpResponseHeader {
    String value();

    class MappingFactory implements HttpReturnMapperFactory<HttpResponseHeader> {
        @Override
        public HttpReturnMapper create(HttpResponseHeader annotation, Type returnType, ApiControllerContext context) {
            String name = annotation.value();
            return (result, exchange) -> exchange.setResponseHeader(name, result.toString());
        }

        @Override
        public HttpClientReturnMapper createClientMapper(HttpResponseHeader annotation, Type returnType) {
            TypeConverter converter = TypeConverterFactory.fromStrings(returnType, "header " + annotation.value());
            return (exchange) -> converter.apply(exchange.getResponseHeaders(annotation.value()));
        }
    }
}


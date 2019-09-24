package org.actioncontroller;

import org.actioncontroller.meta.ApiHttpExchange;
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
@HttpReturnMapping(ContentBody.MappingFactory.class)
public @interface ContentBody {

    String contentType() default "text/plain";

    class MappingFactory implements HttpReturnMapperFactory<ContentBody> {
        @Override
        public HttpReturnMapper create(ContentBody annotation, Class<?> returnType) {
            return (result, exchange) ->
                    exchange.write(annotation.contentType(), writer -> writer.write(String.valueOf(result)));
        }

        @Override
        public HttpClientReturnMapper createClientMapper(ContentBody annotation, Class<?> returnType) {
            return exchange -> ApiHttpExchange.convertParameterType(exchange.getResponseBody(), returnType);
        }
    }
}

package org.actioncontroller;

import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.meta.HttpClientReturnMapper;
import org.actioncontroller.meta.HttpReturnMapperFactory;
import org.actioncontroller.meta.HttpReturnMapping;
import org.actioncontroller.meta.HttpReturnMapper;
import org.actioncontroller.meta.HttpRouterMapping;

import java.io.StringWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Set the return value in the content body with an optional content-type
 *
 * @see HttpReturnMapping
 */
@Retention(RUNTIME)
@Target({METHOD, PARAMETER})
@HttpReturnMapping(ContentBody.MapperFactory.class)
@HttpParameterMapping(ContentBody.ParameterMapperFactory.class)
public @interface ContentBody {

    String contentType() default "text/plain";

    class MapperFactory implements HttpReturnMapperFactory<ContentBody> {
        @Override
        public HttpReturnMapper create(ContentBody annotation, Class<?> returnType) {
            return (result, exchange) ->
                    exchange.write(annotation.contentType(), writer -> writer.write(String.valueOf(result)));
        }

        @Override
        public HttpClientReturnMapper createClientMapper(ContentBody annotation, Type returnType) {
            return exchange -> ApiHttpExchange.convertParameterType(exchange.getResponseBody(), returnType);
        }
    }

    class ParameterMapperFactory implements HttpParameterMapperFactory<ContentBody> {

        @Override
        public HttpParameterMapper create(ContentBody annotation, Parameter parameter, ApiControllerContext context) {
            return exchange -> {
                StringWriter out = new StringWriter();
                exchange.getReader().transferTo(out);
                return out.toString();
            };
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(ContentBody annotation, Parameter parameter) {
            return (exchange, arg) -> exchange.write(annotation.contentType(), writer -> writer.write(String.valueOf(arg)));
        }
    }
}

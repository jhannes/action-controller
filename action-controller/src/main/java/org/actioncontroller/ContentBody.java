package org.actioncontroller;

import org.actioncontroller.client.ApiClientExchange;
import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpClientReturnMapper;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpReturnMapper;
import org.actioncontroller.meta.HttpReturnMapperFactory;
import org.actioncontroller.meta.HttpReturnMapping;

import java.io.ByteArrayOutputStream;
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
        public HttpReturnMapper create(ContentBody annotation, Type returnType) {
            Class<?> returnClass = (Class<?>)returnType;
            if (returnClass.isArray() && returnClass.getComponentType() == byte.class) {
                return (result, exchange) ->
                        exchange.output(annotation.contentType(), output -> output.write((byte[])result));
            }
            return (result, exchange) ->
                    exchange.write(annotation.contentType(), writer -> writer.write(String.valueOf(result)));
        }

        @Override
        public HttpClientReturnMapper createClientMapper(ContentBody annotation, Type returnType) {
            if (returnType instanceof Class<?> && ((Class<?>)returnType).isArray() && ((Class<?>)returnType).getComponentType() == byte.class) {
                return ApiClientExchange::getResponseBodyBytes;
            }
            return exchange -> ApiHttpExchange.convertParameterType(exchange.getResponseBody(), returnType);
        }
    }

    class ParameterMapperFactory implements HttpParameterMapperFactory<ContentBody> {

        @Override
        public HttpParameterMapper create(ContentBody annotation, Parameter parameter, ApiControllerContext context) {
            if (parameter.getType().isArray() && parameter.getType().getComponentType() == byte.class) {
                return exchange -> {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    exchange.getInputStream().transferTo(out);
                    return out.toByteArray();
                };
            }
            return exchange -> {
                StringWriter out = new StringWriter();
                exchange.getReader().transferTo(out);
                return out.toString();
            };
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(ContentBody annotation, Parameter parameter) {
            if (parameter.getType().isArray() && parameter.getType().getComponentType() == byte.class) {
                return (exchange, arg) -> exchange.output(annotation.contentType(), output -> output.write((byte[])arg));
            }
            return (exchange, arg) -> exchange.write(annotation.contentType(), writer -> writer.write(String.valueOf(arg)));
        }
    }
}

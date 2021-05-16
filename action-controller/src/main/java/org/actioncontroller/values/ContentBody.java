package org.actioncontroller.values;

import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.TypeConverterFactory;
import org.actioncontroller.client.ApiClientExchange;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpClientReturnMapper;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpReturnMapper;
import org.actioncontroller.meta.HttpReturnMapperFactory;
import org.actioncontroller.meta.HttpReturnMapping;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.function.Function;

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
            if (InputStream.class.isAssignableFrom(returnClass)) {
                return (result, exchange) -> exchange.output(annotation.contentType(), ((InputStream) result)::transferTo);
            }
            if (Reader.class.isAssignableFrom(returnClass)) {
                return (result, exchange) -> exchange.write(annotation.contentType(), ((Reader) result)::transferTo);
            }
            return (result, exchange) ->
                    exchange.write(annotation.contentType(), writer -> writer.write(String.valueOf(result)));
        }

        @Override
        public HttpClientReturnMapper createClientMapper(ContentBody annotation, Type returnType) {
            if (returnType instanceof Class<?> && ((Class<?>)returnType).isArray() && ((Class<?>)returnType).getComponentType() == byte.class) {
                return exchange -> {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    exchange.getResponseBodyStream().transferTo(buffer);
                    return buffer.toByteArray();
                };
            }
            if (InputStream.class == returnType) {
                return ApiClientExchange::getResponseBodyStream;
            }
            if (BufferedInputStream.class == returnType) {
                return exchange -> new BufferedInputStream(exchange.getResponseBodyStream());
            }
            if (Reader.class == returnType) {
                return ApiClientExchange::getResponseBodyReader;
            }
            if (BufferedReader.class == returnType) {
                return exchange -> new BufferedReader(exchange.getResponseBodyReader());
            }
            Function<String, ?> converter = TypeConverterFactory.fromSingleString(returnType, "content body");

            return new HttpClientReturnMapper() {
                @Override
                public Object getReturnValue(ApiClientExchange exchange) throws IOException {
                    StringWriter buffer = new StringWriter();
                    exchange.getResponseBodyReader().transferTo(buffer);
                    return converter.apply(buffer.toString());
                }

                @Override
                public void setupExchange(ApiClientExchange exchange) {
                    exchange.setHeader("Accept", annotation.contentType());
                }
            };
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

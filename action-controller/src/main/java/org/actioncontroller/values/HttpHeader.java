package org.actioncontroller.values;

import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.TypeConverter;
import org.actioncontroller.TypeConverterFactory;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpClientReturnMapper;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpReturnMapper;
import org.actioncontroller.meta.HttpReturnMapperFactory;
import org.actioncontroller.meta.HttpReturnMapping;
import org.actioncontroller.util.TypesUtil;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * When used on a parameter, maps the HTTP Request header to the parameter, converting the type if necessary.
 * When used on method, maps the return value to the specified HTTP Response header.
 * If the parameter type is Consumer, calling parameter.accept() sets the http header value instead of returning it.
 *
 * @see HttpReturnMapping
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.PARAMETER})
@HttpParameterMapping(HttpHeader.Mapper.class)
@HttpReturnMapping(HttpHeader.Mapper.class)
public @interface HttpHeader {
    String value();

    class Mapper implements
            HttpParameterMapperFactory<HttpHeader>,
            HttpReturnMapperFactory<HttpHeader>
    {
        @Override
        public HttpParameterMapper create(HttpHeader annotation, Parameter parameter, ApiControllerContext context) {
            String name = annotation.value();
            Class<?> type = TypesUtil.getRawType(parameter.getParameterizedType());
            if (type == Consumer.class) {
                return exchange -> (Consumer<?>) o -> exchange.setResponseHeader(name, Objects.toString(o, null));
            } else {
                TypeConverter converter = TypeConverterFactory.fromStrings(type, "header " + name);
                return exchange -> converter.apply(exchange.getHeaders(name));
            }
        }

        @Override
        public HttpReturnMapper create(HttpHeader annotation, Type returnType) {
            return (result, exchange) -> exchange.setResponseHeader(annotation.value(), Objects.toString(result, null));
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(HttpHeader annotation, Parameter parameter) {
            Type parameterType = parameter.getParameterizedType();
            if (TypesUtil.getRawType(parameterType) == Consumer.class) {
                Type typeParameter = TypesUtil.typeParameter(parameterType);
                TypeConverter converter = TypeConverterFactory.fromStrings(typeParameter, "header " + annotation.value());
                return (exchange, arg) -> {
                    if (arg != null) {
                        ((Consumer) arg).accept(converter.apply(exchange.getResponseHeaders(annotation.value())));
                    }
                };
            } else {
                return (exchange, o) -> exchange.setHeader(annotation.value(), o);
            }
        }

        @Override
        public HttpClientReturnMapper createClientMapper(HttpHeader annotation, Type returnType) {
            return (exchange -> exchange.getResponseHeaders(annotation.value()).iterator().next());
        }
    }

}

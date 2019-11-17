package org.actioncontroller.json;

import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.TypesUtil;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpClientReturnMapper;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpReturnMapper;
import org.actioncontroller.meta.HttpReturnMapperFactory;
import org.actioncontroller.meta.HttpReturnMapping;
import org.jsonbuddy.JsonArray;
import org.jsonbuddy.JsonNode;
import org.jsonbuddy.JsonObject;
import org.jsonbuddy.parse.JsonParser;
import org.jsonbuddy.pojo.JsonGenerator;
import org.jsonbuddy.pojo.PojoMapper;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.lang.reflect.Type;
import java.util.List;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({PARAMETER, METHOD})
@HttpParameterMapping(JsonBody.MapperFactory.class)
@HttpReturnMapping(JsonBody.ReturnMapperFactory.class)
public @interface JsonBody {

    class ReturnMapperFactory implements HttpReturnMapperFactory<JsonBody> {
        private static HttpReturnMapper writeJsonNode =
                (o, exchange) -> exchange.write("application/json", ((JsonNode) o)::toJson);

        private static HttpReturnMapper writePojo =
                (o, exchange) -> exchange.write("application/json", writer -> JsonGenerator.generate(o).toJson(writer));

        private static HttpReturnMapper writeStream =
                (o, exchange) -> exchange.write("application/json", writer -> JsonGenerator.generate(
                        ((Stream<?>) o).collect(Collectors.toList())
                ).toJson(writer));

        @Override
        public HttpReturnMapper create(JsonBody annotation, Class<?> returnType) {
            if (JsonNode.class.isAssignableFrom(returnType)) {
                return writeJsonNode;
            } else if (Stream.class.isAssignableFrom(returnType)) {
                return writeStream;
            } else {
                return writePojo;
            }
        }

        @Override
        public HttpClientReturnMapper createClientMapper(JsonBody annotation, Type returnType) {
            if (TypesUtil.isTypeOf(returnType, JsonObject.class)) {
                return exchange -> JsonObject.parse(exchange.getResponseBody());
            } else if (TypesUtil.isTypeOf(returnType, JsonArray.class)) {
                return exchange -> JsonArray.parse(exchange.getResponseBody());
            }

            return TypesUtil.streamType(returnType, this::streamReturnMapper)
                    .or(() -> TypesUtil.listType(returnType, this::listReturnMapper))
                    .orElseThrow(() -> new IllegalArgumentException("Invalid type " + returnType));
        }

        private HttpClientReturnMapper streamReturnMapper(Class<?> type) {
            return exchange -> (PojoMapper.map(JsonArray.parse(exchange.getResponseBody()), type)).stream();
        }

        private HttpClientReturnMapper listReturnMapper(Class<?> type) {
            return exchange -> (PojoMapper.map(JsonArray.parse(exchange.getResponseBody()), type));
        }
    }

    class MapperFactory implements HttpParameterMapperFactory<JsonBody> {
        @Override
        public HttpParameterMapper create(JsonBody annotation, Parameter parameter, ApiControllerContext context) {
            if (JsonNode.class.isAssignableFrom(parameter.getType())) {
                return exchange -> JsonParser.parseNode(exchange.getReader());
            }

            return TypesUtil.listType(parameter.getParameterizedType(), this::listParameterMapper)
                    .orElseGet(() -> exchange -> PojoMapper.map(JsonObject.parse(exchange.getReader()), parameter.getType()));
        }

        private HttpParameterMapper listParameterMapper(Class<?> type) {
            return exchange -> (PojoMapper.map(JsonArray.parse(exchange.getReader()), type));
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(JsonBody annotation, Parameter parameter) {
            return (exchange, o) -> exchange.write("application/json", writer -> JsonGenerator.generate(o).toJson(writer));
        }
    }

}


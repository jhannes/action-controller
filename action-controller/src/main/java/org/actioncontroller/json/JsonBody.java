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

import java.io.StringReader;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({PARAMETER, METHOD})
@HttpParameterMapping(JsonBody.MapperFactory.class)
@HttpReturnMapping(JsonBody.ReturnMapperFactory.class)
public @interface JsonBody {

    enum Naming {
        CAMEL_CASE {
            @Override
            public String transform(String name) {
                return name;
            }
        }, UNDERSCORE {
            @Override
            public String transform(String name) {
                return name.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
            }
        };


        public abstract String transform(String name);
    }

    Naming nameFormat() default Naming.CAMEL_CASE;


    class ReturnMapperFactory implements HttpReturnMapperFactory<JsonBody> {

        @Override
        public HttpReturnMapper create(JsonBody annotation, Type returnType) {
            JsonGenerator jsonGenerator = new JsonGenerator() {
                @Override
                protected String getName(Field field) {
                    return annotation.nameFormat().transform(super.getName(field));
                }

                @Override
                protected String getName(Method getMethod) {
                    return annotation.nameFormat().transform(super.getName(getMethod));
                }
            };
            return (o, exchange) -> exchange.write("application/json", writer -> jsonGenerator.generateNode(o, java.util.Optional.of(returnType)).toJson(writer));
        }

        @Override
        public HttpClientReturnMapper createClientMapper(JsonBody annotation, Type returnType) {
            if (TypesUtil.isTypeOf(returnType, JsonNode.class)) {
                return exchange -> JsonParser.parseNode(new StringReader(exchange.getResponseBody()));
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

        private HttpClientReturnMapper objectReturnMapper(Class<?> type) {
            return exchange -> PojoMapper.map(JsonObject.parse(exchange.getResponseBody()), type);
        }
    }

    class MapperFactory implements HttpParameterMapperFactory<JsonBody> {
        @Override
        public HttpParameterMapper create(JsonBody annotation, Parameter parameter, ApiControllerContext context) {
            if (JsonNode.class.isAssignableFrom(parameter.getType())) {
                return exchange -> JsonParser.parseNode(exchange.getReader());
            }

            return TypesUtil.listType(parameter.getParameterizedType(), this::listParameterMapper)
                    .or(() -> TypesUtil.streamType(parameter.getParameterizedType(), this::streamParameterMapper))
                    .orElseGet(() -> exchange -> PojoMapper.map(JsonObject.read(exchange.getReader()), parameter.getType()));
        }

        private HttpParameterMapper listParameterMapper(Class<?> type) {
            return exchange -> (PojoMapper.map(JsonArray.read(exchange.getReader()), type));
        }

        private HttpParameterMapper streamParameterMapper(Class<?> type) {
            return exchange -> (PojoMapper.map(JsonArray.read(exchange.getReader()), type)).stream();
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(JsonBody annotation, Parameter parameter) {
            return (exchange, o) -> exchange.write("application/json", writer -> JsonGenerator.generate(o).toJson(writer));
        }
    }

}


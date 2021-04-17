package org.actioncontroller.json;

import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.HttpRequestException;
import org.actioncontroller.TypesUtil;
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
import org.jsonbuddy.JsonNode;
import org.jsonbuddy.JsonNull;
import org.jsonbuddy.parse.JsonParser;
import org.jsonbuddy.pojo.JsonGenerator;
import org.jsonbuddy.pojo.PojoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Optional;

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

    boolean buffer() default true;

    class ReturnMapperFactory implements HttpReturnMapperFactory<JsonBody> {
        
        private static final Logger logger = LoggerFactory.getLogger(ReturnMapperFactory.class);

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
            if (annotation.buffer()) {
                return (o, exchange) -> exchange.write(
                        "application/json",
                        writer -> {
                            JsonNode json = jsonGenerator.generateNode(o, Optional.of(returnType));
                            logger.trace("Responding with JSON: {}", json);
                            writer.write(json.toJson());
                        }
                );
            } else {
                return (o, exchange) -> exchange.write(
                        "application/json",
                        writer -> {
                            JsonNode json = jsonGenerator.generateNode(o, Optional.of(returnType));
                            logger.trace("Responding with JSON: {}", json);
                            json.toJson(writer);
                        }
                );
            }
        }

        @Override
        public HttpClientReturnMapper createClientMapper(JsonBody annotation, Type returnType) {
            return jsonMapper(exchange -> PojoMapper.mapType(JsonParser.parse(exchange.getResponseBodyStream()), returnType));
        }

        private HttpClientReturnMapper jsonMapper(HttpClientReturnMapper mapper) {
            return new HttpClientReturnMapper() {
                @Override
                public Object getReturnValue(ApiClientExchange exchange) throws IOException {
                    return mapper.getReturnValue(exchange);
                }

                @Override
                public void setupExchange(ApiClientExchange exchange) {
                    exchange.setHeader("Accept", "application/json");
                }
            };
        }
    }

    class MapperFactory implements HttpParameterMapperFactory<JsonBody> {

        private static final Logger logger = LoggerFactory.getLogger(JsonBody.class);

        private final PojoMapper pojoMapper = PojoMapper.create();

        @Override
        public HttpParameterMapper create(JsonBody annotation, Parameter parameter, ApiControllerContext context) {
            Type type = parameter.getParameterizedType();
            if (parameter.getType() == Optional.class) {
                return exchange -> readPojo(exchange, TypesUtil.typeParameter(type));
            }
            return exchange -> readPojo(exchange, type).orElseThrow(() -> new HttpRequestException("Missing required request body"));
        }

        private Optional<Object> readPojo(ApiHttpExchange exchange, Type targetType) throws IOException {
            JsonNode json = JsonParser.parseNode(exchange.getReader());
            logger.trace("Received JSON: {}", json);
            return mapToPojo(json, targetType);
        }

        private Optional<Object> mapToPojo(JsonNode json, Type targetType) {
            return json != null && !(json instanceof JsonNull) ? Optional.of(pojoMapper.mapToPojo(json, targetType)) : Optional.empty();
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(JsonBody annotation, Parameter parameter) {
            if (parameter.getType() == Optional.class) {
                return (exchange, o) -> exchange.write("application/json", writer -> JsonGenerator.generate(((Optional<?>) o).orElse(null)).toJson(writer));
            }
            return (exchange, o) -> exchange.write("application/json", writer -> JsonGenerator.generate(o).toJson(writer));
        }
    }

}


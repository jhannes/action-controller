package org.actioncontroller.values.json;

import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.ApiHttpExchange;
import org.actioncontroller.exceptions.HttpRequestException;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpClientReturnMapper;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpReturnMapper;
import org.actioncontroller.meta.HttpReturnMapperFactory;
import org.actioncontroller.meta.HttpReturnMapping;
import org.actioncontroller.util.TypesUtil;
import org.jsonbuddy.JsonNode;
import org.jsonbuddy.parse.JsonParser;
import org.jsonbuddy.pojo.JsonGenerator;
import org.jsonbuddy.pojo.PojoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
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

    boolean useDeclaringClassAsTemplate() default false;


    class ReturnMapperFactory implements HttpReturnMapperFactory<JsonBody> {
        
        private static final Logger logger = LoggerFactory.getLogger(ReturnMapperFactory.class);

        @Override
        public HttpReturnMapper create(JsonBody annotation, Type returnType, ApiControllerContext context) {
            return createMapper(getJsonGenerator(annotation), returnType, annotation.buffer());
        }

        protected JsonGenerator getJsonGenerator(JsonBody annotation) {
            return new JsonGenerator(annotation.useDeclaringClassAsTemplate())
                    .withNameTransformer(annotation.nameFormat()::transform);
        }

        protected HttpReturnMapper createMapper(JsonGenerator jsonGenerator, Type returnType, boolean buffer) {
            if (buffer) {
                return (o, exchange) -> exchange.write(
                        "application/json",
                        writer -> {
                            JsonNode json = jsonGenerator.generateNode(o, Optional.of(returnType));
                            if (logger.isTraceEnabled()) {
                                logger.trace("Responding with JSON: {}", json);
                            }
                            writer.write(json.toJson());
                        }
                );
            } else {
                return (o, exchange) -> exchange.write(
                        "application/json",
                        writer -> {
                            JsonNode json = jsonGenerator.generateNode(o, Optional.of(returnType));
                            if (logger.isTraceEnabled()) {
                                logger.trace("Responding with JSON: {}", json);
                            }
                            json.toJson(writer);
                        }
                );
            }
        }

        @Override
        public HttpClientReturnMapper createClientMapper(JsonBody annotation, Type returnType) {
            return createClientMapper(returnType, PojoMapper.create());
        }

        protected HttpClientReturnMapper createClientMapper(Type returnType, PojoMapper pojoMapper) {
            return HttpClientReturnMapper.withHeader(
                    exchange -> pojoMapper.mapToPojo(JsonParser.parse(exchange.getResponseBodyStream()), returnType),
                    "Accept",
                    "application/json"
            );
        }
    }

    class MapperFactory implements HttpParameterMapperFactory<JsonBody> {

        private static final Logger logger = LoggerFactory.getLogger(JsonBody.class);

        @Override
        public HttpParameterMapper create(JsonBody annotation, Parameter parameter, ApiControllerContext context) {
            return createMapper(new PojoMapper(), parameter);
        }

        protected HttpParameterMapper createMapper(PojoMapper pojoMapper, Parameter parameter) {
            Type type = parameter.getParameterizedType();
            if (parameter.getType() == Optional.class) {
                return exchange -> readPojo(pojoMapper, exchange, TypesUtil.typeParameter(type));
            } else {
                return exchange -> readPojo(pojoMapper, exchange, type)
                        .orElseThrow(() -> new HttpRequestException("Missing required request body"));
            }
        }

        protected static Optional<Object> readPojo(PojoMapper pojoMapper, ApiHttpExchange exchange, Type targetType) throws IOException {
            JsonNode json = JsonParser.parseNode(exchange.getReader());
            if (logger.isTraceEnabled()) {
                logger.trace("Received JSON: {}", json);
            }
            return Optional.ofNullable(pojoMapper.mapToPojo(json, targetType));
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(JsonBody annotation, Parameter parameter) {
            JsonGenerator jsonGenerator = new JsonGenerator(annotation.useDeclaringClassAsTemplate());
            return (exchange, o) -> exchange.write(
                    "application/json",
                    writer -> jsonGenerator.generateNode(o).toJson(writer)
            );
        }
    }

}


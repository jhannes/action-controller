package org.actioncontroller.optional.json;

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
import org.actioncontroller.values.json.JsonBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.Function;

/**
 * Converts request JSON to POJO or response POJO to JSON.
 */
@Target({ElementType.METHOD,ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@HttpParameterMapping(Json.MapperFactory.class)
@HttpReturnMapping(Json.MapperFactory.class)
public @interface Json {
    Naming nameFormat() default Naming.CAMEL_CASE;

    enum Naming {
        CAMEL_CASE {
            @Override
            public Function<String, String> getTransformer() {
                return Function.identity();
            }
        },
        UNDERSCORE {
            @Override
            public Function<String, String> getTransformer() {
                return JsonGenerator.UNDERSCORE;
            }
        };

        public abstract Function<String, String> getTransformer();
    }

    class MapperFactory implements HttpParameterMapperFactory<Json>, HttpReturnMapperFactory<Json> {
        private static final Logger logger = LoggerFactory.getLogger(JsonBody.class);

        @Override
        public HttpParameterMapper create(Json annotation, Parameter parameter, ApiControllerContext context) throws Exception {
            PojoMapper pojoMapper = context.getAttribute(PojoMapper.class, PojoMapper::new);
            Type type = parameter.getParameterizedType();
            if (parameter.getType() == Optional.class) {
                return exchange -> readPojo(pojoMapper, exchange, TypesUtil.typeParameter(type));
            } else {
                return exchange -> readPojo(pojoMapper, exchange, type)
                        .orElseThrow(() -> new HttpRequestException("Missing required request body"));
            }
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(Json annotation, Parameter parameter) {
            JsonGenerator generator = new JsonGenerator().withNameTransformer(annotation.nameFormat().getTransformer());
            return (exchange, o) -> exchange.write(
                    "application/json",
                    writer -> writer.write(generator.toJson(o).toString())
            );
        }

        protected static Optional<Object> readPojo(PojoMapper pojoMapper, ApiHttpExchange exchange, Type targetType) throws IOException {
            return Optional.ofNullable(pojoMapper.read(exchange.getReader(), targetType));
        }

        @Override
        public HttpReturnMapper create(Json annotation, Type returnType, ApiControllerContext context) {
            JsonGenerator generator = context.getAttribute(JsonGenerator.class, JsonGenerator::new)
                    .withNameTransformer(annotation.nameFormat().getTransformer());
            return (o, exchange) -> exchange.writeBody("application/json", generator.toJson(o).toString());
        }

        @Override
        public HttpClientReturnMapper createClientMapper(Json annotation, Type returnType) {
            PojoMapper pojoMapper = new PojoMapper();
            return HttpClientReturnMapper.withHeader(
                    exchange -> pojoMapper.read(exchange.getResponseBodyReader(), returnType),
                    "Accept",
                    "application/json"
            );
        }

    }
}

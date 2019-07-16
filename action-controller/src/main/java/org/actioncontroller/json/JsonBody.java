package org.actioncontroller.json;

import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpReturnMapper;
import org.actioncontroller.meta.HttpReturnMapperFactory;
import org.actioncontroller.meta.HttpReturnMapping;
import org.jsonbuddy.JsonNode;
import org.jsonbuddy.parse.JsonParser;
import org.jsonbuddy.pojo.JsonGenerator;
import org.jsonbuddy.pojo.PojoMapper;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
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

        @Override
        public HttpReturnMapper create(JsonBody annotation, Class<?> returnType) {
            return JsonNode.class.isAssignableFrom(returnType) ? writeJsonNode : writePojo;
        }
    }

    class MapperFactory implements HttpParameterMapperFactory<JsonBody> {
        @Override
        public HttpParameterMapper create(JsonBody annotation, Parameter parameter) {
            if (JsonNode.class.isAssignableFrom(parameter.getType())) {
                return exchange -> JsonParser.parse(exchange.getReader());
            } else if (List.class.isAssignableFrom(parameter.getType())) {
                return exchange -> JsonParser.parse(exchange.getReader());
            } else {
                return exchange -> PojoMapper.map(
                        JsonParser.parseToObject(exchange.getReader()),
                        parameter.getType()
                );
            }
        }
    }

}


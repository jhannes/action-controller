package org.actioncontroller.json;

import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpRequestParameterMapping;
import org.actioncontroller.meta.HttpResponseValueMapping;
import org.actioncontroller.meta.HttpReturnMapping;
import org.jsonbuddy.JsonNode;
import org.jsonbuddy.parse.JsonParser;
import org.jsonbuddy.pojo.JsonGenerator;
import org.jsonbuddy.pojo.PojoMapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({PARAMETER, METHOD})
@HttpParameterMapping(JsonBody.JsonRequestMapper.class)
@HttpReturnMapping(JsonBody.JsonResponseMapper.class)
public @interface JsonBody {

    class JsonResponseMapper implements HttpResponseValueMapping {

        private static HttpResponseValueMapping writeJsonNode = (o, resp) -> {
            resp.setContentType("application/json");
            ((JsonNode) o).toJson(resp.getWriter());
        };

        private static HttpResponseValueMapping writePojo = (o, resp) -> {
            resp.setContentType("application/json");
            JsonGenerator.generate(o).toJson(resp.getWriter());
        };

        private final HttpResponseValueMapping responseMapping;

        public JsonResponseMapper(JsonBody jsonBody, Class<?> returnType) {
            if (!JsonNode.class.isAssignableFrom(returnType)) {
                this.responseMapping = writePojo;
            } else {
                this.responseMapping = writeJsonNode;
            }
        }

        @Override
        public void accept(Object o, HttpServletResponse resp) throws IOException {
            this.responseMapping.accept(o, resp);
        }
    }

    class JsonRequestMapper implements HttpRequestParameterMapping {

        private static HttpRequestParameterMapping readJsonNode = (req, pathParams) -> JsonParser.parse(req.getReader());

        private HttpRequestParameterMapping responseMapping;

        public JsonRequestMapper(JsonBody jsonBody, Parameter parameter) {
            if (JsonNode.class.isAssignableFrom(parameter.getType())) {
                responseMapping = readJsonNode;
            } else if (List.class.isAssignableFrom(parameter.getType())) {
                throw new IllegalArgumentException(parameter + " must be a JSON type");
            } else {
                responseMapping = (req, u) -> PojoMapper.map(
                        JsonParser.parseToObject(req.getReader()),
                        parameter.getType()
                );
            }
        }

        @Override
        public Object apply(HttpServletRequest req, Map<String, String> u) throws IOException {
            return responseMapping.apply(req, u);
        }
    }
}


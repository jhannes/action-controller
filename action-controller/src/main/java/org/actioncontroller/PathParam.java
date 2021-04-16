package org.actioncontroller;

import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.util.function.Function;

/**
 * Maps part of the HTTP request target to the parameter, converting the type if necessary. For example
 * &#064;Get("/foo/:id") void myMethod(@PathParam("id") int id) maps the second segment of the path to the id parameter.
 *
 * @see HttpParameterMapping
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@HttpParameterMapping(PathParam.MapperFactory.class)
public @interface PathParam {

    String value();

    class MapperFactory implements HttpParameterMapperFactory<PathParam> {
        @Override
        public HttpParameterMapper create(PathParam annotation, Parameter parameter, ApiControllerContext context) {
            String name = annotation.value();
            Function<String, Object> converter = TypeConverterFactory.fromSingleString(parameter.getParameterizedType(), "path parameter " + name);
            return (exchange) -> converter.apply(exchange.pathParam(name));
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(PathParam annotation, Parameter parameter) {
            String name = annotation.value();
            return ((exchange, arg) -> {
                String pathInfo = exchange.getPathInfo();
                pathInfo = pathInfo.replaceAll("/:" + name + "|/\\{" + name + "}", "/" + arg.toString());
                exchange.setPathInfo(pathInfo);
            });
        }
    }
}

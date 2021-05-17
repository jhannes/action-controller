package org.actioncontroller.values;

import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.TypeConverter;
import org.actioncontroller.TypeConverterFactory;
import org.actioncontroller.util.TypesUtil;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpParameterMapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.List;
import java.util.Optional;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
@HttpParameterMapping(IfModifiedSince.Mapper.class)
public @interface IfModifiedSince {
    class Mapper implements HttpParameterMapperFactory<IfModifiedSince> {

        public static final String HEADER_NAME = "If-ModifiedSince";

        @Override
        public HttpParameterMapper create(IfModifiedSince annotation, Parameter parameter, ApiControllerContext context) throws Exception {
            TypeConverter converter = TypeConverterFactory.fromStrings(parameter.getParameterizedType(), "header " + HEADER_NAME);
            return exchange -> converter.apply(exchange.getHeaders(HEADER_NAME));
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(IfModifiedSince annotation, Parameter parameter) {
            if (TypesUtil.getRawType(parameter.getParameterizedType()) == Optional.class) {
                return (exchange, obj) -> ((Optional<?>)obj).ifPresent(o -> exchange.setHeader(HEADER_NAME, DateTimeFormatter.RFC_1123_DATE_TIME.format((Temporal)o)));
            } else {
                return (exchange, obj) -> exchange.setHeader(HEADER_NAME, DateTimeFormatter.RFC_1123_DATE_TIME.format((Temporal)obj));
            }
        }
    }
}

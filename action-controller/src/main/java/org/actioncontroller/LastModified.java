package org.actioncontroller;

import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpParameterMapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
@HttpParameterMapping(LastModified.Mapper.class)
public @interface LastModified {

    String HEADER_NAME = "Last-Modified";

    class Mapper implements HttpParameterMapperFactory<LastModified> {
        @Override
        public HttpParameterMapper create(LastModified annotation, Parameter parameter, ApiControllerContext context) throws Exception {
            if (parameter.getType() != Consumer.class || TypesUtil.getRawType(TypesUtil.typeParameter(parameter.getParameterizedType())).isAssignableFrom(TemporalAccessor.class)) {
                throw new ActionControllerConfigurationException("@LastModified must have Consumer<Instant>, Consumer<ZonedDateTime> or Consumer<OffsetDateTime> parameter, was " + parameter.getParameterizedType());
            }
            return exchange -> (Consumer<?>) (time) -> exchange.setResponseHeader(HEADER_NAME, DateTimeFormatter.RFC_1123_DATE_TIME.format((TemporalAccessor) time));
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(LastModified annotation, Parameter parameter) {
            if (parameter.getType() != Consumer.class || TypesUtil.getRawType(TypesUtil.typeParameter(parameter.getParameterizedType())).isAssignableFrom(Temporal.class)) {
                throw new ActionControllerConfigurationException("@LastModified must have Consumer<Instant>, Consumer<ZonedDateTime> or Consumer<OffsetDateTime> parameter, was " + parameter.getParameterizedType());
            }
            Function<List<String>, ?> converter = TypeConverterFactory.fromStrings(
                    ((ParameterizedType) parameter.getParameterizedType()).getActualTypeArguments()[0]
                , "header " + HEADER_NAME);
            return (exchange, arg) -> {
                String responseHeader = exchange.getResponseHeader(HEADER_NAME);
                ((Consumer)arg).accept(converter.apply(responseHeader != null ? List.of(responseHeader) : null));
            };
        }
    }
}

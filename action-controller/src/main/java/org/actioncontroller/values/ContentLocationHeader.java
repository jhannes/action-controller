package org.actioncontroller.values;

import org.actioncontroller.TypeConverterFactory;
import org.actioncontroller.meta.HttpClientReturnMapper;
import org.actioncontroller.meta.HttpReturnMapper;
import org.actioncontroller.meta.HttpReturnMapperFactory;
import org.actioncontroller.meta.HttpReturnMapping;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Set the method return value into the Content-location response header as String or URL. Strings are interpreted relative to api path
 *
 * @see HttpReturnMapping
 */
@Retention(RUNTIME)
@Target(METHOD)
@HttpReturnMapping(ContentLocationHeader.MappingFactory.class)
public @interface ContentLocationHeader {

    String value() default "";

    String FIELD_NAME = "Content-Location";

    class MappingFactory implements HttpReturnMapperFactory<ContentLocationHeader> {
        @Override
        public HttpReturnMapper create(ContentLocationHeader annotation, Type returnType) {
            if (returnType == URL.class) {
                return (result, exchange) -> exchange.setResponseHeader(FIELD_NAME, result.toString());
            }
            if (annotation.value().isEmpty()) {
                return (result, exchange) ->
                        exchange.setResponseHeader(FIELD_NAME, exchange.getApiURL() + result.toString());
            } else {
                return (result, exchange) -> {
                    String response = annotation.value().replaceFirst("\\{[^}]+}", result.toString());
                    exchange.setResponseHeader(FIELD_NAME, exchange.getApiURL() + response);
                };
            }
        }

        @Override
        public HttpClientReturnMapper createClientMapper(ContentLocationHeader annotation, Type returnType) {
            if (annotation.value().isEmpty()) {
                return exchange -> exchange.getResponseHeader(ContentLocationHeader.FIELD_NAME);
            } else {
                Pattern pattern = Pattern.compile(annotation.value().replaceFirst("\\{[^}]+}", "([^/]+)"));
                Function<String, ?> converter = TypeConverterFactory.fromSingleString(returnType, "path parameter " + annotation.value());
                return exchange -> {
                    String contentLocationHeader = exchange.getResponseHeader(ContentLocationHeader.FIELD_NAME);
                    if (!contentLocationHeader.startsWith(exchange.getApiURL())) {
                        throw new IllegalArgumentException("Expected content-location <" + contentLocationHeader + "> to start with <" + exchange.getApiURL() + ">");
                    }
                    String relativePath = contentLocationHeader.substring(exchange.getApiURL().length());
                    Matcher matcher = pattern.matcher(relativePath);
                    if (!matcher.matches()) {
                        throw new IllegalArgumentException("Expected content-location <" + relativePath + "> to match <" + annotation.value() + ">");
                    }
                    return converter.apply(matcher.group(1));
                };
            }
        }
    }
}


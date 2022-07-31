package org.actioncontroller.values;

import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.ApiHttpExchange;
import org.actioncontroller.client.ApiClientExchange;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.util.TypesUtil;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Maps the parameter to the specified HTTP request cookie, URL decoding and converting the type if necessary.
 * If the session parameter is missing, aborts the request with 401 Unauthorized, unless the parameter type is Optional.
 * If the parameter type is Consumer, calling parameter.accept() sets the cookie value instead of returning it
 * converting the type and URL encoding.
 *
 * @see HttpParameterMapping
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@HttpParameterMapping(UnencryptedCookie.Factory.class)
public @interface UnencryptedCookie {

    /**
     * The name of the cookie
     */
    String value();

    /**
     * If false, omits the Secure flag on the cookie, meaning that the cookie will be sent over HTTP as well as HTTPS.
     * For testing purposes, action-controller always omits the Secure flag for localhost addresses
     */
    boolean secure() default true;

    /**
     * If false, omits the HttpOnly flag on the cookie, meaning that the cookie can be read by client-side JavaScript.
     * You normally want HttpOnly to mitigate the impact of Cross-site Scripting attacks
     */
    boolean isHttpOnly() default true;

    class Factory implements HttpParameterMapperFactory<UnencryptedCookie> {

        @Override
        public HttpParameterMapper create(UnencryptedCookie annotation, Parameter parameter, ApiControllerContext context) {
            Class<?> type = TypesUtil.getRawType(parameter.getParameterizedType());
            if (type == Consumer.class) {
                return new UnencryptedCookie.ConsumerMapper(annotation, parameter);
            } else if (type == AtomicReference.class) {
                return new UnencryptedCookie.AtomicReferenceMapper(annotation, parameter);
            } else {
                return new UnencryptedCookie.Mapper(annotation, parameter);
            }
        }

        private Map<String, Object> combine(Map<String, Object> defaultValues, Map<String, String> extra) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>(defaultValues);
            result.putAll(extra);
            return result;
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(UnencryptedCookie annotation, Parameter parameter) {
            Type parameterType = parameter.getParameterizedType();
            if (parameter.getType() == Consumer.class) {
                return new ConsumerMapper(annotation, parameter);
            } else if (parameter.getType() == AtomicReference.class) {
                return new AtomicReferenceMapper(annotation, parameter);
            } else {
                return new Mapper(annotation, parameter);
            }
        }
    }

    class Mapper implements HttpParameterMapper, HttpClientParameterMapper {
        private final UnencryptedCookie annotation;
        private final String name;
        private final TypeConverter converter;

        public Mapper(UnencryptedCookie annotation, Parameter parameter) {
            this.name = annotation.value();
            this.annotation = annotation;
            this.converter = TypeConverterFactory.fromStrings(parameter.getParameterizedType(), "cookie " + name);
        }

        @Override
        public Object apply(ApiHttpExchange exchange) throws Exception {
            return converter.apply(exchange.getCookies(name));
        }

        @Override
        public void apply(ApiClientExchange exchange, Object arg) throws IOException {
            if (arg != null) {
                exchange.addRequestCookie(name, arg);
            }
        }
    }

    class ConsumerMapper implements HttpParameterMapper, HttpClientParameterMapper {

        protected final String name;
        private final UnencryptedCookie annotation;
        private final TypeConverter converter;

        public ConsumerMapper(UnencryptedCookie annotation, Parameter parameter) {
            this.name = annotation.value();
            this.annotation = annotation;
            this.converter = TypeConverterFactory.fromStrings(TypesUtil.typeParameter(parameter.getParameterizedType()), "cookie " + annotation.value());
        }

        @Override
        public Object apply(ApiHttpExchange exchange) {
            return (Consumer<?>) o -> setResponseCookie(exchange, o);
        }

        protected void setResponseCookie(ApiHttpExchange exchange, Object o) {
            exchange.setCookie(name, Objects.toString(o, null), annotation.secure(), annotation.isHttpOnly());
        }

        private Map<String, Object> combine(Map<String, Object> defaultValues, Map<String, String> extra) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>(defaultValues);
            result.putAll(extra);
            return result;
        }

        @Override
        public void apply(ApiClientExchange exchange, Object arg) throws IOException {
            if (arg != null) {
                ((Consumer) arg).accept(converter.apply(getResponseCookies(exchange)));
            }
        }

        protected List<String> getResponseCookies(ApiClientExchange exchange) {
            return exchange.getResponseCookies(name);
        }
    }


    class AtomicReferenceMapper extends ConsumerMapper {
        private final TypeConverter converter;

        public AtomicReferenceMapper(UnencryptedCookie annotation, Parameter parameter) {
            super(annotation, parameter);
            this.converter = TypeConverterFactory.fromStrings(parameter.getParameterizedType(), "cookie " + annotation.value());
        }

        @Override
        public Object apply(ApiHttpExchange exchange) {
            return converter.apply(exchange.getCookies(name));
        }

        @Override
        public void onComplete(ApiHttpExchange exchange, Object argument) {
            Object o = ((AtomicReference<?>) argument).get();
            setResponseCookie(exchange, o);
        }

        @Override
        public void apply(ApiClientExchange exchange, Object atomicReference) throws IOException {
            if (atomicReference != null) {
                Object arg = ((AtomicReference<?>) atomicReference).get();
                if (arg != null) exchange.addRequestCookie(name, arg);
                AtomicReference value = (AtomicReference) converter.apply(getResponseCookies(exchange));
                ((AtomicReference) atomicReference).set(value.get());
            }
        }
    }
}

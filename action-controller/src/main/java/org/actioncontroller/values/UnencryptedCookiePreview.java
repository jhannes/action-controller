package org.actioncontroller.values;

import org.actioncontroller.ActionControllerCookie;
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
import java.util.stream.Collectors;

/**
 * <strong>This is a preview version of @UnencryptedCookie, which supports cookies with the Same-Site attribute set</strong>
 * <p>
 * Maps the parameter to the specified HTTP request cookie, URL decoding and converting the type if necessary.
 * If the session parameter is missing, aborts the request with 401 Unauthorized, unless the parameter type is Optional.
 * If the parameter type is Consumer, calling parameter.accept() sets the cookie value instead of returning it
 * converting the type and URL encoding.
 *
 * @see HttpParameterMapping
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@HttpParameterMapping(UnencryptedCookiePreview.Factory.class)
public @interface UnencryptedCookiePreview {
    enum SameSite {
        Strict, Lax, None, NotSet
    }

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

    /**
     * If set, sets the SameSite attribute on the cookie. Legal values are: Strict (never send the cookie to other hosts),
     * Lax (send the cookie to other sites with the same domain, even if the subdomain and port differ),
     * None (send the cookie to all sites)
     */
    SameSite sameSite() default SameSite.NotSet;

    /**
     * If set, sets the Comment attribute on the cookie
     */
    String comment() default "";

    class Factory implements HttpParameterMapperFactory<UnencryptedCookiePreview> {

        @Override
        public HttpParameterMapper create(UnencryptedCookiePreview annotation, Parameter parameter, ApiControllerContext context) {
            Class<?> type = TypesUtil.getRawType(parameter.getParameterizedType());
            if (type == Consumer.class) {
                return new ConsumerMapper(annotation, parameter);
            } else if (type == AtomicReference.class) {
                return new AtomicReferenceMapper(annotation, parameter);
            } else {
                return new Mapper(annotation, parameter);
            }
        }

        private Map<String, Object> combine(Map<String, Object> defaultValues, Map<String, String> extra) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>(defaultValues);
            result.putAll(extra);
            return result;
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(UnencryptedCookiePreview annotation, Parameter parameter) {
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
        private final String name;
        private final TypeConverter converter;

        public Mapper(UnencryptedCookiePreview annotation, Parameter parameter) {
            this.name = annotation.value();
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
        private final UnencryptedCookiePreview annotation;
        private final TypeConverter converter;

        public ConsumerMapper(UnencryptedCookiePreview annotation, Parameter parameter) {
            this.name = annotation.value();
            this.annotation = annotation;
            this.converter = TypeConverterFactory.fromStrings(TypesUtil.typeParameter(parameter.getParameterizedType()), "cookie " + annotation.value());
        }

        @Override
        public Object apply(ApiHttpExchange exchange) {
            return (Consumer<?>) o -> setResponseCookie(exchange, o);
        }

        protected void setResponseCookie(ApiHttpExchange exchange, Object o) {
            exchange.addResponseHeader("Set-Cookie", createCookie(o, exchange.getContextPath()).toStringRFC6265());
        }

        protected ActionControllerCookie createCookie(Object o, String contextPath) {
            return new ActionControllerCookie(name, Objects.toString(o, null))
                    .path(contextPath)
                    .secure(annotation.secure()).httpOnly(annotation.isHttpOnly()).setAttribute("Comment", annotation.comment())
                    .sameSite(annotation.sameSite() == SameSite.NotSet ? null : annotation.sameSite().toString());
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
            List<ActionControllerCookie> responseCookies = ActionControllerCookie
                    .parseSetCookieHeaders(exchange.getResponseHeaders("Set-Cookie"));
            return responseCookies.stream()
                    .filter(ActionControllerCookie::isUnexpired)
                    .filter(c -> c.getName().equalsIgnoreCase(name))
                    .map(ActionControllerCookie::getValue)
                    .collect(Collectors.toList());
        }
    }


    class AtomicReferenceMapper extends ConsumerMapper {
        private final TypeConverter converter;

        public AtomicReferenceMapper(UnencryptedCookiePreview annotation, Parameter parameter) {
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

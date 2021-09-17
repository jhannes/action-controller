package org.actioncontroller.values.json;

import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.util.TypesUtil;
import org.actioncontroller.ApiHttpExchange;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.exceptions.ActionControllerConfigurationException;
import org.jsonbuddy.JsonNode;
import org.jsonbuddy.JsonObject;
import org.jsonbuddy.pojo.JsonGenerator;
import org.jsonbuddy.pojo.PojoMapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;

/**
 * Create a cookie that is parsed to and from JSON. If <code>setInResponse = true</code>,
 * the cookie will be written back to the response after the method completes. If the cookie
 * is not read, it is initialized as "{}" (an empty JSON object)
 * For example:
 *
 * <pre>
 * &#064;POST("/changeUser")
 * public void changeUser(
 *     &#064;UnencryptedJsonCookie(value = "userSession", setInResponse = true) Map&lt;String, String&gt; userSession,
 *     &#064;RequestParameter("newUserName") String newUserName
 * ) {
 *    logger.info("Changing username from {} to {}", userSession.get("username"), newUserName);
 *    userSession.set("username");
 * }
 * </pre>
 *
 * The first time this method (or any like it) is called over http, the client gets a cookie
 * with a value like <code>{"username": "something"}</code>. Every time the method is called,
 * a new cookie is returned to the client
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@HttpParameterMapping(UnencryptedJsonCookie.MapperFactory.class)
public @interface UnencryptedJsonCookie {

    /**
     * The name of the cookie
     */
    String value() default "";

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

    boolean setInResponse() default false;

    class MapperFactory implements HttpParameterMapperFactory<UnencryptedJsonCookie> {

        @Override
        public HttpParameterMapper create(UnencryptedJsonCookie annotation, Parameter parameter, ApiControllerContext context) {
            boolean optional = parameter.getType() == Optional.class;
            Type type = optional ? TypesUtil.typeParameter(parameter.getParameterizedType()) : parameter.getParameterizedType();
            return new Mapper(new PojoMapper(), getCookieName(annotation, type), type, annotation);
        }

        private String getCookieName(UnencryptedJsonCookie annotation, Type type) {
            if (!annotation.value().equals("")) {
                return annotation.value();
            } else if (Map.class.isAssignableFrom(TypesUtil.getRawType(type)) || JsonNode.class.isAssignableFrom(TypesUtil.getRawType(type))) {
                throw new ActionControllerConfigurationException("Missing cookie name");
            } else {
                return TypesUtil.getRawType(type).getSimpleName();
            }
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(UnencryptedJsonCookie annotation, Parameter parameter) {
            boolean optional = parameter.getType() == Optional.class;
            Type type = optional ? TypesUtil.typeParameter(parameter.getParameterizedType()) : parameter.getParameterizedType();
            String name = getCookieName(annotation, type);
            return (exchange, arg) -> {
                if (arg != null) {
                    exchange.addRequestCookie(name, JsonGenerator.generate(arg).toJson());
                }
            };
        }

        public static class Mapper implements HttpParameterMapper {
            private final String name;
            private final Type type;
            private final UnencryptedJsonCookie annotation;
            private final PojoMapper pojoMapper;

            public Mapper(PojoMapper pojoMapper, String name, Type type, UnencryptedJsonCookie annotation) {
                this.name = name;
                this.type = type;
                this.annotation = annotation;
                this.pojoMapper = pojoMapper;
            }

            @Override
            public Object apply(ApiHttpExchange exchange) {
                return getCookie(exchange, name, type);
            }

            protected Object getCookie(ApiHttpExchange exchange, String name, Type type) {
                return exchange.getCookies(name).stream()
                        .map(JsonObject::parse).map(json -> pojoMapper.mapToPojo(json, type))
                        .findFirst()
                        .orElseGet(() -> pojoMapper.mapToPojo(new JsonObject(), type));
            }

            @Override
            public void onComplete(ApiHttpExchange exchange, Object argument) {
                if (annotation.setInResponse()) {
                    exchange.setCookie(name, JsonGenerator.generate(argument).toJson(), annotation.secure(), annotation.isHttpOnly());
                }
            }
        }
    }
}

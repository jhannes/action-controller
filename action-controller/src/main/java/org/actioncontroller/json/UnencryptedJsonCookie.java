package org.actioncontroller.json;

import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.TypesUtil;
import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.ActionControllerConfigurationException;
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
            boolean setInResponse = annotation.setInResponse();
                boolean optional = parameter.getType() == Optional.class;
            Type type = optional ? TypesUtil.typeParameter(parameter.getParameterizedType()) : parameter.getParameterizedType();
            String name;
            if (!annotation.value().equals("")) {
                name = annotation.value();
            } else if (Map.class.isAssignableFrom(TypesUtil.getRawType(type)) || JsonNode.class.isAssignableFrom(TypesUtil.getRawType(type))) {
                throw new ActionControllerConfigurationException("Missing cookie name");
            } else {
                name = TypesUtil.getRawType(type).getSimpleName();
            }
            return new HttpParameterMapper() {
                @Override
                public Object apply(ApiHttpExchange exchange) {
                    return MapperFactory.this.getCookie(exchange, name, type);
                }

                @Override
                public void onComplete(ApiHttpExchange exchange, Object argument) {
                    if (setInResponse) {
                        exchange.setCookie(name, JsonGenerator.generate(argument).toJson(), annotation.secure(), annotation.isHttpOnly());
                    }
                }
            };
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(UnencryptedJsonCookie annotation, Parameter parameter) {
            boolean optional = parameter.getType() == Optional.class;
            Type type = optional ? TypesUtil.typeParameter(parameter.getParameterizedType()) : parameter.getParameterizedType();
            String name = annotation.value().equals("") ? ((Class<?>)type).getSimpleName() : annotation.value();
            return (exchange, arg) -> {
                if (arg != null) {
                    exchange.addRequestCookie(name, JsonGenerator.generate(arg).toJson());
                }
            };
        }

        protected Object getCookie(ApiHttpExchange exchange, String name, Type type) {
            return exchange.getCookie(name)
                    .map(JsonObject::parse).map(json -> PojoMapper.mapType(json, type))
                    .orElseGet(() -> PojoMapper.mapType(new JsonObject(), type));
        }

    }
}

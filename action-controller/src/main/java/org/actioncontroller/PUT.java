package org.actioncontroller;

import org.actioncontroller.meta.ApiControllerActionFactory;
import org.actioncontroller.meta.HttpRouterMapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

/**
 * Specifies that this method should handle HTTP PUT requests
 *
 * @see HttpRouterMapping
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@HttpRouterMapping(PUT.ActionFactory.class)
public @interface PUT {

    String value();

    class ActionFactory implements ApiControllerActionFactory<PUT> {
        @Override
        public ApiControllerMethodAction create(PUT annotation, Object controller, Method action, ApiControllerContext context) {
            return new ApiControllerMethodAction("PUT", annotation.value(), controller, action, context);
        }
    }

}

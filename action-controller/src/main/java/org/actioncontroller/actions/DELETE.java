package org.actioncontroller.actions;

import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.ApiControllerMethodAction;
import org.actioncontroller.meta.ApiControllerActionFactory;
import org.actioncontroller.meta.HttpRouterMapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

/**
 * Specifies that this method should handle HTTP DELETE requests
 *
 * @see HttpRouterMapping
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@HttpRouterMapping(DELETE.RouterMapperFactory.class)
public @interface DELETE {

    String value();

    class RouterMapperFactory implements ApiControllerActionFactory<DELETE> {
        @Override
        public ApiControllerMethodAction create(DELETE annotation, Object controller, Method action, ApiControllerContext context) {
            return new ApiControllerMethodAction("DELETE", annotation.value(), controller, action, context);
        }
    }
}

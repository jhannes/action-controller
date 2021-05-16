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
 * Specifies that this method should handle HTTP GET requests.
 *
 * @see HttpRouterMapping
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@HttpRouterMapping(GET.ActionFactory.class)
public @interface GET {

    String value();

    class ActionFactory implements ApiControllerActionFactory<GET> {
        @Override
        public ApiControllerMethodAction create(GET annotation, Object controller, Method action, ApiControllerContext context) {
            return new ApiControllerMethodAction("GET", annotation.value(), controller, action, context);
        }
    }

}

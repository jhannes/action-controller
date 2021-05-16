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
 * Specifies that this method should handle HTTP POST requests
 *
 * @see HttpRouterMapping
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@HttpRouterMapping(POST.ActionFactory.class)
public @interface POST {

    String value();

    class ActionFactory implements ApiControllerActionFactory<POST> {
        @Override
        public ApiControllerMethodAction create(POST annotation, Object controller, Method action, ApiControllerContext context) {
            return new ApiControllerMethodAction("POST", annotation.value(), controller, action, context);
        }
    }


}

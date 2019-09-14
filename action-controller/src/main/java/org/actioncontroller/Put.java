package org.actioncontroller;

import org.actioncontroller.meta.ApiControllerActionFactory;
import org.actioncontroller.meta.HttpRouterMapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@HttpRouterMapping(Put.ActionFactory.class)
public @interface Put {

    String value();

    class ActionFactory implements ApiControllerActionFactory<Put> {
        @Override
        public ApiControllerMethodAction create(Put annotation, Object controller, Method action) {
            return new ApiControllerMethodAction("PUT", annotation.value(), controller, action);
        }
    }

}

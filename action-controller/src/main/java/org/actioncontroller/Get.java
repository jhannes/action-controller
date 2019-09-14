package org.actioncontroller;

import org.actioncontroller.meta.ApiControllerActionFactory;
import org.actioncontroller.meta.HttpRouterMapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@HttpRouterMapping(Get.ActionFactory.class)
public @interface Get {

    String value();

    class ActionFactory implements ApiControllerActionFactory<Get> {
        @Override
        public ApiControllerMethodAction create(Get annotation, Object controller, Method action) {
            return new ApiControllerMethodAction("GET", annotation.value(), controller, action);
        }
    }

}

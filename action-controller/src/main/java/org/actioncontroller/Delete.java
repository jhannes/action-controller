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
@HttpRouterMapping(Delete.RouterMapperFactory.class)
public @interface Delete {

    String value();

    class RouterMapperFactory implements ApiControllerActionFactory<Delete> {
        @Override
        public ApiControllerMethodAction create(Delete annotation, Object controller, Method action) {
            return new ApiControllerMethodAction("DELETE", annotation.value(), controller, action);
        }
    }
}

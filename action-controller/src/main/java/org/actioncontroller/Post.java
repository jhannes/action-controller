package org.actioncontroller;

import org.actioncontroller.meta.ApiControllerActionFactory;
import org.actioncontroller.meta.HttpRouterMapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

/**
 * Specifies that this method should handle HTTP POST requests
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@HttpRouterMapping(Post.ActionFactory.class)
public @interface Post {

    String value();

    class ActionFactory implements ApiControllerActionFactory<Post> {
        @Override
        public ApiControllerMethodAction create(Post annotation, Object controller, Method action, ApiControllerContext context) {
            return new ApiControllerMethodAction("POST", annotation.value(), controller, action, context);
        }
    }


}

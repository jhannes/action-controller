package org.actioncontroller.meta;

import org.actioncontroller.ApiControllerAction;
import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.actions.GET;
import org.actioncontroller.actions.POST;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Implementations of this interface should be annotated on annotations used to
 * convert methods to HTTP actions, such as {@link GET} and {@link POST}
 */
public interface ApiControllerActionFactory<ANNOTATION extends Annotation> extends AnnotationFactory {

    ApiControllerAction create(ANNOTATION annotation, Object controller, Method action, ApiControllerContext context);

    static Optional<ApiControllerAction> createNewInstance(Object controller, ApiControllerContext context, Method method) {
        return AnnotationFactory.getAnnotatedAnnotation(method, HttpRouterMapping.class)
                .map(annotation -> newInstance(annotation).create(annotation, controller, method, context));
    }

    static <T extends Annotation> ApiControllerActionFactory<T> newInstance(T annotation) {
        //noinspection unchecked
        return AnnotationFactory.newInstance(annotation.annotationType().getAnnotation(HttpRouterMapping.class).value());
    }


}

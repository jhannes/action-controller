package org.actioncontroller.meta;

import org.actioncontroller.ApiControllerAction;
import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.GET;
import org.actioncontroller.POST;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Implementations of this interface should be annotated on annotations used to
 * convert methods to HTTP actions, such as {@link GET} and {@link POST}
 */
public interface ApiControllerActionFactory<ANNOTATION extends Annotation> {

    ApiControllerAction create(ANNOTATION annotation, Object controller, Method action, ApiControllerContext context);

}

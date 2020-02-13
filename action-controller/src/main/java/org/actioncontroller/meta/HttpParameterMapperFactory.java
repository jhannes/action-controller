package org.actioncontroller.meta;

import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.client.ApiClientClassProxy;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;

/**
 * Implementations of this interface should be annotated on annotations used to
 * convert HTTP requests into method invocation arguments, such as
 * {@link org.actioncontroller.RequestParam}, {@link org.actioncontroller.PathParam}
 * and {@link org.actioncontroller.UnencryptedCookie}
 */
public interface HttpParameterMapperFactory<ANNOTATION extends Annotation> {

    /**
     * Create a mapper to convert a {@link ApiHttpExchange} into method call arguments.
     */
    HttpParameterMapper create(ANNOTATION annotation, Parameter parameter, ApiControllerContext context) throws Exception;

    /**
     * Used by {@link ApiClientClassProxy} to convert
     * method arguments into HTTP request information.
     */
    default HttpClientParameterMapper clientParameterMapper(ANNOTATION annotation, Parameter parameter) {
        throw new UnsupportedOperationException(getClass() + " does not support " + HttpClientReturnMapper.class.getName());
    }

}

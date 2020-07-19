package org.actioncontroller.meta;

import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.ExceptionUtil;
import org.actioncontroller.client.ApiClientClassProxy;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.Optional;

/**
 * Implementations of this interface should be annotated on annotations used to
 * convert HTTP requests into method invocation arguments, such as
 * {@link org.actioncontroller.RequestParam}, {@link org.actioncontroller.PathParam}
 * and {@link org.actioncontroller.UnencryptedCookie}
 */
public interface HttpParameterMapperFactory<ANNOTATION extends Annotation> extends AnnotationFactory {

    static Optional<HttpParameterMapper> createNewInstance(Parameter parameter, ApiControllerContext context) {
        return AnnotationFactory.getAnnotatedAnnotation(parameter, HttpParameterMapping.class)
                .map(value -> createFactory(value).safeCreate(value, parameter, context));
    }

    static <T extends Annotation> HttpParameterMapperFactory<T> createFactory(T annotation) {
        //noinspection unchecked
        return AnnotationFactory.newInstance(annotation.annotationType().getAnnotation(HttpParameterMapping.class).value());
    }

    /**
     * Create a mapper to convert a {@link ApiHttpExchange} into method call arguments.
     */
    HttpParameterMapper create(ANNOTATION annotation, Parameter parameter, ApiControllerContext context) throws Exception;

    default HttpParameterMapper safeCreate(ANNOTATION annotation, Parameter parameter, ApiControllerContext context) {
        try {
            return create(annotation, parameter, context);
        } catch (Exception e) {
            throw ExceptionUtil.softenException(e);
        }
    }

    /**
     * Used by {@link ApiClientClassProxy} to convert
     * method arguments into HTTP request information.
     */
    default HttpClientParameterMapper clientParameterMapper(ANNOTATION annotation, Parameter parameter) {
        throw new UnsupportedOperationException(getClass() + " does not support " + HttpClientReturnMapper.class.getName());
    }



}

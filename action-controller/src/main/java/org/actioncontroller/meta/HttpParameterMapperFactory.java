package org.actioncontroller.meta;

import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.ApiHttpExchange;
import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.util.ExceptionUtil;
import org.actioncontroller.values.PathParam;
import org.actioncontroller.values.RequestParam;
import org.actioncontroller.values.UnencryptedCookie;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.Optional;

/**
 * Implementations of this interface should be annotated on annotations used to
 * convert HTTP requests into method invocation arguments, such as
 * {@link RequestParam}, {@link PathParam}
 * and {@link UnencryptedCookie}
 */
public interface HttpParameterMapperFactory<ANNOTATION extends Annotation> extends AnnotationFactory {

    static Optional<HttpParameterMapper> createNewInstance(Parameter parameter, ApiControllerContext context) {
        return AnnotationFactory.getAnnotatedAnnotation(parameter, HttpParameterMapping.class)
                .map(annotation -> createFactory(annotation).safeCreate(annotation, parameter, context));
    }

    static Optional<HttpClientParameterMapper> createNewClientInstance(Parameter parameter) {
        return AnnotationFactory.getAnnotatedAnnotation(parameter, HttpParameterMapping.class)
                .map(annotation -> createFactory(annotation).clientParameterMapper(annotation, parameter));
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

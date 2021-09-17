package org.actioncontroller.meta;

import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.values.ContentBody;
import org.actioncontroller.values.SendRedirect;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Optional;

/**
 * Implementations of this interface should be annotated on annotations used to
 * convert method invocation return values to HTTP response information, such as
 * {@link ContentBody} and {@link SendRedirect}
 */
public interface HttpReturnMapperFactory<ANNOTATION extends Annotation> extends AnnotationFactory {

    static Optional<HttpReturnMapper> createNewInstance(Method action, ApiControllerContext context) {
        return AnnotationFactory.getAnnotatedAnnotation(action, HttpReturnMapping.class)
                .map(annotation -> createFactory(annotation).create(annotation, action.getGenericReturnType(), context));
    }

    static Optional<HttpClientReturnMapper> createNewClientInstance(Method action) {
        return AnnotationFactory.getAnnotatedAnnotation(action, HttpReturnMapping.class)
                .map(annotation -> createFactory(annotation).createClientMapper(annotation, action.getGenericReturnType()));
    }

    static <T extends Annotation> HttpReturnMapperFactory<T> createFactory(T annotation) {
        //noinspection unchecked
        return AnnotationFactory.newInstance(annotation.annotationType().getAnnotation(HttpReturnMapping.class).value());
    }

    HttpReturnMapper create(ANNOTATION annotation, Type returnType, ApiControllerContext context);

    default HttpClientReturnMapper createClientMapper(ANNOTATION annotation, Type returnType) {
        throw new UnsupportedOperationException(getClass() + " does not support " + HttpClientReturnMapper.class);
    }
}

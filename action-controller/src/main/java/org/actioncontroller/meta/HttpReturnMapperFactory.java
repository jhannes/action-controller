package org.actioncontroller.meta;

import java.lang.annotation.Annotation;

/**
 * Implementations of this interface should be annotated on annotations used to
 * convert method invocation return values to HTTP response information, such as
 * {@link org.actioncontroller.ContentBody} and {@link org.actioncontroller.SendRedirect}
 */
public interface HttpReturnMapperFactory<ANNOTATION extends Annotation> {
    HttpReturnMapper create(ANNOTATION annotation, Class<?> returnType);

    default HttpClientReturnMapper createClientMapper(ANNOTATION annotation, Class<?> returnType) {
        throw new UnsupportedOperationException(getClass() + " does not support " + HttpClientReturnMapper.class);
    }
}

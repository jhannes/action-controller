package org.actioncontroller.meta;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Implementations of this interface should be annotated on annotations used to
 * convert method invocation return values to HTTP response information, such as
 * {@link org.actioncontroller.ContentBody} and {@link org.actioncontroller.SendRedirect}
 */
public interface HttpReturnMapperFactory<ANNOTATION extends Annotation> {
    HttpReturnMapper create(ANNOTATION annotation, Type returnType);

    default HttpClientReturnMapper createClientMapper(ANNOTATION annotation, Type returnType) {
        throw new UnsupportedOperationException(getClass() + " does not support " + HttpClientReturnMapper.class);
    }
}

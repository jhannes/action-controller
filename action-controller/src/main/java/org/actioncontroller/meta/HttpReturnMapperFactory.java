package org.actioncontroller.meta;

import java.lang.annotation.Annotation;

public interface HttpReturnMapperFactory<ANNOTATION extends Annotation> {
    HttpReturnMapper create(ANNOTATION annotation, Class<?> returnType);

    default HttpClientReturnMapper createClientMapper(ANNOTATION annotation, Class<?> returnType) {
        throw new UnsupportedOperationException(getClass() + " does not support " + HttpClientReturnMapper.class);
    }
}

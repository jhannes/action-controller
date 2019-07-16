package org.actioncontroller.meta;

import java.lang.annotation.Annotation;

public interface HttpReturnMapperFactory<ANNOTATION extends Annotation> {
    HttpReturnMapper create(ANNOTATION annotation, Class<?> returnType);
}

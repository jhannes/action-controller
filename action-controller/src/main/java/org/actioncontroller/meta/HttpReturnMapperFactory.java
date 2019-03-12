package org.actioncontroller.meta;

import java.lang.annotation.Annotation;

public interface HttpReturnMapperFactory<ANNOTATION extends Annotation> {
    HttpReturnValueMapping create(ANNOTATION annotation, Class<?> returnType);
}

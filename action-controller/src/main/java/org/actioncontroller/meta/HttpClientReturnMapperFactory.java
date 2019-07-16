package org.actioncontroller.meta;

import java.lang.annotation.Annotation;

public interface HttpClientReturnMapperFactory<ANNOTATION extends Annotation> {
    HttpClientReturnMapper createClient(ANNOTATION annotation, Class<?> returnType);
}

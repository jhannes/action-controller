package org.actioncontroller.meta;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;

@FunctionalInterface
public interface HttpRequestParameterMappingFactory<ANNOTATION extends Annotation> {

    HttpRequestParameterMapping create(ANNOTATION annotation, Parameter parameter);

}

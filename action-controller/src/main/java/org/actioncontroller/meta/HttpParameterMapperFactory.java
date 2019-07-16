package org.actioncontroller.meta;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;

@FunctionalInterface
public interface HttpParameterMapperFactory<ANNOTATION extends Annotation> {

    HttpParameterMapper create(ANNOTATION annotation, Parameter parameter);

}

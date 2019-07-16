package org.actioncontroller.meta;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;

public interface HttpClientParameterMapperFactory<ANNOTATION extends Annotation> {

    HttpClientParameterMapper createClient(ANNOTATION annotation, Parameter parameter);

}

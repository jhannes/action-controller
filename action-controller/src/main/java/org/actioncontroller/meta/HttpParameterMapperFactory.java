package org.actioncontroller.meta;

import org.actioncontroller.ApiControllerContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;

public interface HttpParameterMapperFactory<ANNOTATION extends Annotation> {

    HttpParameterMapper create(ANNOTATION annotation, Parameter parameter, ApiControllerContext context) throws Exception;

    default HttpClientParameterMapper clientParameterMapper(ANNOTATION annotation, Parameter parameter) {
        throw new UnsupportedOperationException(getClass() + " does not support " + HttpClientReturnMapper.class.getName());
    }

}

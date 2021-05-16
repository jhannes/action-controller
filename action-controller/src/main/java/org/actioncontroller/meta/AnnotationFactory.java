package org.actioncontroller.meta;

import org.actioncontroller.util.ExceptionUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

public interface AnnotationFactory {

    static <T> T newInstance(Class<? extends T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | IllegalArgumentException | SecurityException e) {
            throw ExceptionUtil.softenException(e);
        } catch (InvocationTargetException e) {
            throw ExceptionUtil.softenException(e.getTargetException());
        }
    }

    static <T extends Annotation> Optional<Annotation> getAnnotatedAnnotation(AnnotatedElement element, Class<T> targetAnnotation) {
        for (Annotation routingAnnotation : element.getAnnotations()) {
            T routerMapping = routingAnnotation.annotationType().getAnnotation(targetAnnotation);
            if (routerMapping != null) {
                return Optional.of(routingAnnotation);
            }
        }
        return Optional.empty();
    }
}

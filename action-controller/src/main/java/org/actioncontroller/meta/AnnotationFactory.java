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

    /**
     * Returns any annotation instance on the annotated element which is itself annotated with the target
     * annotation. For example, given the annotation and class:
     * 
     * <pre>
     * &#064;Retention(RetentionPolicy.RUNTIME)
     * &#064;Target(ElementType.ANNOTATION_TYPE)
     * public &#064;interface MyAnnotation {
     *
     *     String value();
     *
     * }
     * 
     * &#064;MyAnnotation("hello")
     * public class MyClass {
     *     
     * }
     * </pre>
     * 
     * Then <code>getAnnotatedAnnotation(MyClass.class, MyAnnotation.class).value()</code> will return
     * <code>"hello"</code>.
     */
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

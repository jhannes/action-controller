package org.actioncontroller;


import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpRequestParameterMapping;
import org.actioncontroller.meta.HttpRequestParameterMappingFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.Optional;
import java.util.function.Consumer;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@HttpParameterMapping(SessionParameter.MappingFactory.class)
public @interface SessionParameter {

    String value() default "";

    boolean createIfMissing() default false;

    boolean invalidate() default false;

    class MappingFactory implements HttpRequestParameterMappingFactory<SessionParameter> {
        @Override
        public HttpRequestParameterMapping create(SessionParameter annotation, Parameter parameter) {
            String name = annotation.value();
            if (parameter.getType() == Consumer.class) {
                if (name.isEmpty()) {
                    name = ((AnnotatedParameterizedType) parameter.getAnnotatedType())
                            .getAnnotatedActualTypeArguments()[0].getType().getTypeName();
                }
                return new CreateSession(name, annotation.invalidate());
            }

            if (name.isEmpty()) {
                name = parameter.getType().getName();
            }
            if (annotation.createIfMissing()) {
                Constructor<?> constructor;
                try {
                    constructor = parameter.getType().getConstructor();
                } catch (NoSuchMethodException e) {
                    throw new IllegalArgumentException("When createIfMissing=true, " + parameter.getType() + " should have default constructor");
                }
                return new AutoCreateSession(name, annotation.invalidate(), constructor);
            }

            return new SessionParameterMapping(parameter, name);
        }
    }

    class AutoCreateSession implements HttpRequestParameterMapping {

        private final String name;
        private final boolean invalidate;
        private final Constructor<?> constructor;

        public AutoCreateSession(String name, boolean invalidate, Constructor<?> constructor) {
            this.name = name;
            this.invalidate = invalidate;
            this.constructor = constructor;
        }

        @Override
        public Object apply(ApiHttpExchange exchange) {
            return exchange.getSessionAttribute(name)
                    .orElseGet(() -> {
                        Object newValue = newInstance();
                        exchange.setSessionAttribute(name, newValue, invalidate);
                        return newValue;
                    });
        }

        private Object newInstance() {
            try {
                return constructor.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw ExceptionUtil.softenException(e);
            } catch (InvocationTargetException e) {
                throw ExceptionUtil.softenException(e.getTargetException());
            }
        }
    }

    class CreateSession implements HttpRequestParameterMapping {

        private String name;
        private boolean invalidate;

        public CreateSession(String name, boolean invalidate) {
            this.name = name;
            this.invalidate = invalidate;
        }

        @Override
        public Object apply(ApiHttpExchange exchange) {
            return (Consumer<Object>) o -> exchange.setSessionAttribute(name, o, invalidate);
        }
    }


    class SessionParameterMapping implements HttpRequestParameterMapping {

        private Parameter parameter;
        private String value;

        public SessionParameterMapping(Parameter parameter, String name) {
            this.parameter = parameter;
            this.value = name;
        }

        @Override
        public Object apply(ApiHttpExchange exchange) {
            Optional value = exchange.getSessionAttribute(this.value);
            if (parameter.getType() == Optional.class) {
                return Optional.ofNullable(value);
            } else if (value.isPresent()) {
                return value.get();
            } else {
                throw new HttpActionException(401, "Missing required session parameter " + this.value);
            }
        }
    }

}

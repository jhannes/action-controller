package org.actioncontroller;


import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.util.Optional;
import java.util.function.Consumer;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@HttpParameterMapping(SessionParameter.MapperFactory.class)
public @interface SessionParameter {

    String value() default "";

    boolean createIfMissing() default false;

    boolean invalidate() default false;

    boolean changeSessionId() default false;

    class MapperFactory implements HttpParameterMapperFactory<SessionParameter> {
        @Override
        public HttpParameterMapper create(SessionParameter annotation, Parameter parameter) {
            String name = annotation.value();
            if (parameter.getType() == Consumer.class) {
                if (name.isEmpty()) {
                    name = ((AnnotatedParameterizedType) parameter.getAnnotatedType())
                            .getAnnotatedActualTypeArguments()[0].getType().getTypeName();
                }
                return new CreateSession(name, annotation.invalidate());
            }

            if (name.isEmpty()) {
                if (parameter.getType() == Optional.class) {
                    name = ((ParameterizedType)parameter.getParameterizedType()).getActualTypeArguments()[0].getTypeName();
                } else {
                    name = parameter.getType().getName();
                }
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

            return new SessionParameterMapper(parameter, name);
        }

        @Override
        public HttpClientParameterMapper clientParameterMapper(SessionParameter annotation, Parameter parameter) {
            return (exchange, arg) -> {};
        }
    }

    class AutoCreateSession implements HttpParameterMapper {

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
            return exchange.getSessionAttribute(name, true)
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

    class CreateSession implements HttpParameterMapper {

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


    class SessionParameterMapper implements HttpParameterMapper {

        private Parameter parameter;
        private String name;

        public SessionParameterMapper(Parameter parameter, String name) {
            this.parameter = parameter;
            this.name = name;
            assert !name.startsWith(Optional.class.getName());
        }

        @Override
        public Object apply(ApiHttpExchange exchange) {
            Optional value = exchange.getSessionAttribute(this.name, false);
            if (parameter.getType() == Optional.class) {
                return value;
            } else if (value.isPresent()) {
                return value.get();
            } else {
                throw new HttpActionException(401, "Missing required session parameter " + this.name);
            }
        }
    }

}

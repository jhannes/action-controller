package org.actioncontroller;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public class TypesUtil {
    public static boolean isTypeOf(Type actualType, Class<?> targetType) {
        return targetType.isAssignableFrom(getRawType(actualType));
    }

    public static Class<?> getRawType(Type type) {
        return type instanceof Class ? (Class<?>)type : getRawType(((ParameterizedType)type).getRawType());
    }

    public static <T> Optional<T> streamType(Type type, Function<Class<?>, T> mapper) {
        if (type instanceof ParameterizedType && Stream.class.isAssignableFrom(getRawType(type))) {
            return Optional.of(mapper.apply(typeParameter(type)));
        } else {
            return Optional.empty();
        }
    }

    public static <T> Optional<T> listType(Type type, Function<Class<?>, T> mapper) {
        if (type instanceof ParameterizedType && List.class.isAssignableFrom(getRawType(type))) {
            return Optional.of(mapper.apply(typeParameter(type)));
        } else {
            return Optional.empty();
        }
    }


    private static Class<?> typeParameter(Type type) {
        return (Class<?>) ((ParameterizedType) type).getActualTypeArguments()[0];
    }
}

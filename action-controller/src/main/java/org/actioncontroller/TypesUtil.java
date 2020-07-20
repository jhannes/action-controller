package org.actioncontroller;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class TypesUtil {
    public static boolean isTypeOf(Type actualType, Class<?> targetType) {
        return targetType.isAssignableFrom(getRawType(actualType));
    }

    public static Class<?> getRawType(Type type) {
        return type instanceof Class ? (Class<?>)type : getRawType(((ParameterizedType)type).getRawType());
    }

    public static Class<?> typeParameter(Type type) {
        return (Class<?>) ((ParameterizedType) type).getActualTypeArguments()[0];
    }
}

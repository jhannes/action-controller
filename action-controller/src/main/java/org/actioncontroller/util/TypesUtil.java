package org.actioncontroller.util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

public class TypesUtil {

    public static Class<?> getRawType(Type type) {
        return type instanceof Class ? (Class<?>)type : getRawType(((ParameterizedType)type).getRawType());
    }

    public static Type typeParameter(Type type) {
        return ((ParameterizedType) type).getActualTypeArguments()[0];
    }

    public static boolean isInstanceOf(Type targetType, Class<?> type) {
        return targetType instanceof Class && ((Class<?>)targetType).isAssignableFrom(type);
    }

}

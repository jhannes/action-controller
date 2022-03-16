package org.actioncontroller.optional.json;

import org.actioncontroller.util.ExceptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Convert from JSON structures to regular Java objects. Supports primitive numbers
 * (byte, short, int, long, float, double, BigDecimal, BigInteger), simple values
 * (for String, character, Instant, LocalDate, ZonedDateTime, OffsetDateTime, UUID,
 * URL, URI and InetAddress), Enums, Collections, Maps, Streams and Java beans.
 * You can even add your own converters.
 *
 * <h2>Usage:</h2>
 *
 * <pre>
 * PojoMapper pojoMapper = new PojoMapper();
 * pojoMapper.addObjectMapper(MyClass.class, (json, mapper) -> new MyClass());
 * MyClass o = pojoMapper.map(json, MyClass.class);
 * </pre>
 */
public class PojoMapper {
    private static final Logger logger = LoggerFactory.getLogger(PojoMapper.class);

    private static final Map<Class<?>, Class<?>> WRAPPER_TYPES = Map.of(
            boolean.class, Boolean.class,
            byte.class, Byte.class,
            char.class, Character.class,
            double.class, Double.class,
            float.class, Float.class,
            int.class, Integer.class,
            long.class, Long.class,
            short.class, Short.class,
            void.class, Void.class
    );

    private static ObjectFromJsonMapper<?> stringMapper(Function<String, Object> stringFunction) {
        return (JsonValue o, PojoMapper mapper) -> stringFunction.apply(((JsonString)o).getString());
    }

    private static <T> void numberMapper(Class<T> type, Function<JsonNumber, T> numberFunction) {
        defaultMappers.put(type, (JsonValue o, PojoMapper mapper) -> numberFunction.apply((JsonNumber)o));
    }


    private static final Map<Type, ObjectFromJsonMapper<?>> defaultMappers = new HashMap<>();
    static {
        defaultMappers.put(boolean.class, (json, mapper) -> json.equals(JsonValue.TRUE));
        defaultMappers.put(Boolean.class, (json, mapper) -> json.equals(JsonValue.TRUE));
        numberMapper(byte.class, n -> (byte)n.intValue());
        numberMapper(Byte.class, n -> (byte)n.intValue());
        numberMapper(short.class, n -> (short)n.intValue());
        numberMapper(Short.class, n -> (short)n.intValue());
        numberMapper(int.class, JsonNumber::intValue);
        numberMapper(Integer.class, JsonNumber::intValue);
        numberMapper(long.class, JsonNumber::longValue);
        numberMapper(Long.class, JsonNumber::longValue);
        numberMapper(float.class, n -> n.numberValue().floatValue());
        numberMapper(Float.class, n -> n.numberValue().floatValue());
        numberMapper(double.class, JsonNumber::doubleValue);
        numberMapper(Double.class, JsonNumber::doubleValue);
        numberMapper(BigDecimal.class, JsonNumber::bigDecimalValue);
        numberMapper(BigInteger.class, JsonNumber::bigIntegerValueExact);
        defaultMappers.put(char.class, stringMapper(s -> s.charAt(0)));
        defaultMappers.put(Character.class, stringMapper(s -> s.charAt(0)));
        defaultMappers.put(String.class, stringMapper(s -> s));
        defaultMappers.put(LocalDate.class, stringMapper(LocalDate::parse));
        defaultMappers.put(ZonedDateTime.class, stringMapper(ZonedDateTime::parse));
        defaultMappers.put(OffsetDateTime.class, stringMapper(OffsetDateTime::parse));
        defaultMappers.put(Instant.class, stringMapper(Instant::parse));
        defaultMappers.put(UUID.class, stringMapper(UUID::fromString));
        defaultMappers.put(URL.class, (JsonValue o, PojoMapper mapper) -> {
            try {
                return new URL(((JsonString) o).getString());
            } catch (MalformedURLException e) {
                throw ExceptionUtil.softenException(e);
            }
        });
        defaultMappers.put(URI.class, (o, mapper) -> {
            try {
                return new URI(((JsonString) o).getString());
            } catch (URISyntaxException e) {
                throw ExceptionUtil.softenException(e);
            }
        });
        defaultMappers.put(InetAddress.class, (o, mapper) -> {
            try {
                return InetAddress.getByName(((JsonString) o).getString());
            } catch (UnknownHostException e) {
                throw ExceptionUtil.softenException(e);
            }
        });
    }

    private final Map<Type, ObjectFromJsonMapper<?>> mappers = new HashMap<>(defaultMappers);

    /**
     * Map from json to a object of the specified target class
     */
    public <T> T map(JsonValue json, Class<T> targetType) {
        return map(json, (Type)targetType);
    }

    /**
     * Map from json to a object of the specified target type
     */
    public <T> T map(JsonValue json, Type targetType) {
        if (json == null || json == JsonValue.NULL) {
            return null;
        }
        return verifyType(doMap(json, targetType), targetType);
    }

    private <T> T verifyType(T o, Type targetType) {
        if (targetType instanceof Class && ((Class<?>)targetType).isPrimitive()) {
            if (WRAPPER_TYPES.get(targetType) != o.getClass()) {
                throw new IllegalArgumentException(o + " is was " + o.getClass() + " not " + targetType);
            }
        } else if (!getRawType(targetType).isAssignableFrom(o.getClass())) {
            throw new IllegalArgumentException(o + " is was " + o.getClass() + " not " + targetType);
        }
        return o;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> T doMap(JsonValue json, Type targetType) {
        if (mappers.containsKey(targetType)) {
            return (T) mappers.get(targetType).apply(json, this);
        }
        if (Collection.class.isAssignableFrom(getClassType(targetType))) {
            return (T) collectionMapper(targetType).apply(json, this);
        }
        if (Stream.class.isAssignableFrom(getClassType(targetType))) {
            return (T)((Collection) collectionMapper(targetType).apply(json, this)).stream();
        }
        if (getClassType(targetType).isArray()) {
            return (T) arrayMapper(targetType).apply(json, this);
        }
        if (Map.class.isAssignableFrom(getClassType(targetType))) {
            return (T) mapMapper(targetType).apply(json, this);
        }
        if (Optional.class.isAssignableFrom(getClassType(targetType))) {
            return (T) optionalMapper(targetType).apply(json, this);
        }
        if (Enum.class.isAssignableFrom(getClassType(targetType))) {
            return (T) enumMapper((Class) targetType).apply(json, this);
        }
        return (T) objectMapper(targetType).apply(json, this);
    }

    private ObjectFromJsonMapper<?> objectMapper(Type targetType) {
        return (json, pojoMapper) -> {
            if (!(json instanceof JsonObject)) {
                throw new IllegalArgumentException("Cannot map to " + targetType + " from " + json);
            }
            Object o = newInstance(targetType);
            writeFields((JsonObject) json, o);
            return o;
        };
    }

    protected Object newInstance(Type targetType) {
        try {
            return ((Class<?>)targetType).getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw ExceptionUtil.softenException(e);
        }
    }

    private ObjectFromJsonMapper<?> optionalMapper(Type targetType) {
        Type elementType = ((ParameterizedType) targetType).getActualTypeArguments()[0];
        return (json, pojoMapper) -> json != null ? Optional.of(map(json, elementType)) : Optional.empty();
    }

    private <T extends Enum<T>> ObjectFromJsonMapper<?> enumMapper(Class<T> targetType) {
        return (json, pojoMapper) -> {
            String stringValue = pojoMapper.map(json, String.class);
            return (Enum<T>) Enum.valueOf(targetType, stringValue);
        };
    }

    private ObjectFromJsonMapper<?> mapMapper(Type targetType) {
        ParameterizedType genericType = (ParameterizedType) targetType;
        Type keyType = genericType.getActualTypeArguments()[0];
        Type valueType = genericType.getActualTypeArguments()[1];
        return (json, pojoMapper) -> {
            Map<?, ?> result = new HashMap<>();
            for (String key : ((JsonObject) json).keySet()) {
                result.put(map(Json.createValue(key), keyType), map(((JsonObject) json).get(key), valueType));
            }
            return result;
        };
    }

    private ObjectFromJsonMapper<?> arrayMapper(Type targetType) {
        Class<?> elementType = getClassType(targetType).getComponentType();
        return (json, pojoMapper) -> {
            JsonArray array = (JsonArray) json;
            Object result = Array.newInstance(elementType, array.size());
            for (int i = 0; i < array.size(); i++) {
                Array.set(result, i, pojoMapper.map(array.get(i), elementType));
            }
            return result;
        };
    }

    private ObjectFromJsonMapper<?> collectionMapper(Type targetType) {
        Type elementClass = getElementClass(targetType);
        return (json, pojoMapper) -> {
            ArrayList<Object> result = new ArrayList<>();
            ((JsonArray) json).forEach(e -> result.add(pojoMapper.map(e, elementClass)));
            return result;
        };
    }

    private Type getElementClass(Type type) {
        if (!(type instanceof ParameterizedType)) {
            return Object.class;
        }
        ParameterizedType genericReturnType = (ParameterizedType) type;
        return genericReturnType.getActualTypeArguments()[0];
    }

    public <T> T writeFields(JsonObject json, T o) {
        for (String property : json.keySet()) {
            try {
                Method method = findSetter(o, property);
                if (method != null) {
                    Object value = map(json.get(property), firstParameterType(o.getClass(), method));
                    method.invoke(o, value);
                    continue;
                }
                Field field = o.getClass().getDeclaredField(property);
                field.set(o, map(json.get(property), field.getGenericType()));
            } catch (ClassCastException e) {
                throw new RuntimeException("Failed to map " + o.getClass() + "." + property + " " + e);
            } catch (NoSuchFieldException ignored) {
                if (findGetter(o, property) == null) {
                    logger.info("Could no map field {} on {}", property, o.getClass());
                }
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw ExceptionUtil.softenException(e);
            }
        }
        return o;
    }

    private Type firstParameterType(Class<?> objectClass, Method method) {
        Type parameterType = method.getGenericParameterTypes()[0];
        if (parameterType instanceof TypeVariable) {
            int typeParameterIndex = Stream.of(((TypeVariable<?>)parameterType).getGenericDeclaration().getTypeParameters())
                    .map(TypeVariable::getName)
                    .collect(Collectors.toList())
                    .indexOf(((TypeVariable<?>)parameterType).getName());

            Type genericSuperclass = objectClass.getGenericSuperclass();
            while (genericSuperclass != null) {
                if (getRawType(genericSuperclass) == getRawType((Type)((TypeVariable<?>)parameterType).getGenericDeclaration())) {
                    return ((ParameterizedType)genericSuperclass).getActualTypeArguments()[typeParameterIndex];
                }
                genericSuperclass = getRawType(genericSuperclass).getGenericSuperclass();
            }
            throw new IllegalArgumentException("Could not find type of " + parameterType + " in " + objectClass);
        }
        return parameterType;
    }

    private <T> Method findSetter(T o, String property) {
        String setterName = setterName(property);
        return Arrays.stream(o.getClass().getMethods())
                .filter(method -> setterName.equals(method.getName()) || property.equals(method.getName()))
                .filter(method -> method.getParameterCount() == 1)
                .findAny()
                .orElse(null);
    }

    private <T> Method findGetter(T o, String property) {
        String getterName = getterName(property);
        return Arrays.stream(o.getClass().getMethods())
                .filter(method -> getterName.equals(method.getName()) || property.equals(method.getName()))
                .filter(method -> method.getParameterCount() == 0)
                .findAny()
                .orElse(null);
    }

    protected String getterName(String fieldName) {
        return "get" + (fieldName.length() > 0 ? Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1) : "");
    }

    protected String setterName(String fieldName) {
        return "set" + (fieldName.length() > 0 ? Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1) : "");
    }

    public PojoMapper addMapper(Type type, ObjectFromJsonMapper<?> mapper) {
        mappers.put(type, mapper);
        return this;
    }

    public <T> PojoMapper addObjectMapper(Class<T> type, ObjectFromJsonMapper<T> objectMapper) {
        return addMapper(type, objectMapper);
    }


    protected Class<?> getClassType(Type type) {
        if (type instanceof ParameterizedType) {
            return getClassType(((ParameterizedType)type).getRawType());
        }
        return (Class<?>) type;
    }

    private Class<?> getRawType(Type type) {
        return type instanceof ParameterizedType
                ? getRawType(((ParameterizedType) type).getRawType())
                : (Class<?>) type;
    }
}


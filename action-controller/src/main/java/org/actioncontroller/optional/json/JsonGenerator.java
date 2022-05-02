package org.actioncontroller.optional.json;

import org.actioncontroller.util.ExceptionUtil;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonPatch;
import javax.json.JsonPatchBuilder;
import javax.json.JsonValue;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Convert from regular Java objects to JSON structures. Supports primitive numbers
 * (byte, short, int, long, float, double, BigDecimal, BigInteger), simple values
 * (for String, character, Instant, LocalDate, ZonedDateTime, OffsetDateTime, UUID,
 * URL, URI and InetAddress), Enums, Collections, Maps, Streams and Java beans.
 * You can even add your own converters.
 *
 * <h2>Usage:</h2>
 *
 * <pre>
 * JsonGenerator jsonGenerator = new JsonGenerator();
 * jsonGenerator.addMapper(MyClass.class, o -&gt; new JsonObject());
 * JsonValue json = jsonGenerator.toJson(o);
 * </pre>
 */
public class JsonGenerator {

    public static final Function<String, String> UNDERSCORE = name -> name.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    private Function<String, String> nameTransformer = Function.identity();

    @SuppressWarnings("unchecked")
    private static <T> void addDefaultMapper(Class<T> type, Function<T, JsonValue> mapper) {
        defaultMappers.put(type, o -> mapper.apply((T)o));
    }

    private static <T> void addToStringMapper(Class<T> type) {
        defaultMappers.put(type, o -> (o != null ? Json.createValue(o.toString()) : null));
    }

    private static final Map<Type, Function<Object, JsonValue>> defaultMappers = new HashMap<>();
    static {
        defaultMappers.put(Boolean.class, v -> ((boolean) v) ? JsonValue.TRUE : JsonValue.FALSE);
        addDefaultMapper(byte.class, Json::createValue);
        addDefaultMapper(Byte.class, Json::createValue);
        addDefaultMapper(short.class, Json::createValue);
        addDefaultMapper(Short.class, Json::createValue);
        addDefaultMapper(int.class, Json::createValue);
        addDefaultMapper(Integer.class, Json::createValue);
        addDefaultMapper(long.class, Json::createValue);
        addDefaultMapper(Long.class, Json::createValue);
        addDefaultMapper(float.class, Json::createValue);
        addDefaultMapper(Float.class, Json::createValue);
        addDefaultMapper(double.class, Json::createValue);
        addDefaultMapper(Double.class, Json::createValue);
        addDefaultMapper(char.class, value -> Json.createValue(String.valueOf(value)));
        addDefaultMapper(Character.class, value -> Json.createValue(String.valueOf(value)));
        addDefaultMapper(BigDecimal.class, Json::createValue);
        addDefaultMapper(BigInteger.class, Json::createValue);
        addToStringMapper(String.class);
        addToStringMapper(LocalDate.class);
        addToStringMapper(Instant.class);
        addToStringMapper(ZonedDateTime.class);
        addToStringMapper(OffsetDateTime.class);
        addToStringMapper(UUID.class);
        addToStringMapper(URL.class);
        addToStringMapper(URI.class);
        defaultMappers.put(InetAddress.class, o -> (o != null ? Json.createValue(((InetAddress)o).getHostName()) : null));
        defaultMappers.put(Inet4Address.class, o -> (o != null ? Json.createValue(((InetAddress)o).getHostName()) : null));
        defaultMappers.put(Inet6Address.class, o -> (o != null ? Json.createValue(((InetAddress)o).getHostName()) : null));
    }

    private final Map<Type, Function<Object, JsonValue>> mappers = new HashMap<>(defaultMappers);


    /**
     * Register a mapper for the specified type
     */
    public <T> void addMapper(Class<T> type, Function<T, JsonValue> mapper) {
        //noinspection unchecked
        mappers.put(type, (Function<Object, JsonValue>)mapper);
    }

    /**
     * Register a mapper for the specified type
     */
    public void addMapper(Type type, Function<Object, JsonValue> mapper) {
        mappers.put(type, mapper);
    }

    public JsonGenerator withNameTransformer(Function<String, String> transformer) {
        this.nameTransformer = transformer;
        return this;
    }

    /**
     * Used to map a Java object to a Json structure
     */
    public JsonValue toJson(Object pojo) {
        if (pojo == null) {
            return null;
        }
        if (pojo instanceof JsonValue) {
            return (JsonValue)pojo;
        }
        if (pojo instanceof JsonObjectBuilder) {
            return ((JsonObjectBuilder)pojo).build();
        }
        if (mappers.containsKey(pojo.getClass())) {
            return mappers.get(pojo.getClass()).apply(pojo);
        }
        if (pojo instanceof Enum) {
            return Json.createValue(pojo.toString());
        }
        if (pojo instanceof Map) {
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            for (Map.Entry<?, ?> o : ((Map<?, ?>) pojo).entrySet()) {
                objectBuilder.add(o.getKey().toString(), toJson(o.getValue()));
            }
            return objectBuilder.build();
        }
        if (pojo instanceof Collection) {
            JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            ((Collection<?>) pojo).forEach(o -> arrayBuilder.add(toJson(o)));
            return arrayBuilder.build();
        }
        if (pojo instanceof Stream) {
            JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            ((Stream<?>) pojo).forEach(o -> arrayBuilder.add(toJson(o)));
            return arrayBuilder.build();
        }
        if (pojo.getClass().isArray()) {
            JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            Arrays.asList((Object[])pojo).forEach(o -> arrayBuilder.add(toJson(o)));
            return arrayBuilder.build();
        }
        if (pojo instanceof Optional) {
            return ((Optional<?>) pojo).map(this::toJson).orElse(JsonValue.NULL);
        }
        return objectToJson(pojo);
    }

    public JsonObject objectToJson(Object pojo) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        for (Method method : pojo.getClass().getMethods()) {
            if (isGetMethod(method)) {
                try {
                    Object value = method.invoke(pojo);
                    if (value != null) {
                        builder.add(getFieldName(method), toJson(value));
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw ExceptionUtil.softenException(e);
                }
            }
        }
        for (Field field : pojo.getClass().getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                try {
                    Object value = field.get(pojo);
                    if (value != null) {
                        builder.add(field.getName(), toJson(value));
                    }
                } catch (IllegalAccessException e) {
                    throw ExceptionUtil.softenException(e);
                }
            }
        }

        return builder.build();
    }

    protected String getFieldName(Method method) {
        String methodName = method.getName();
        return transformName(Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4));
    }

    protected String transformName(String propertyName) {
        return nameTransformer.apply(propertyName);
    }

    private boolean isGetMethod(Method method) {
        if (method.getDeclaringClass() == Object.class || method.getDeclaringClass() == Enum.class) {
            return false;
        }
        return method.getName().startsWith("get") && method.getParameterTypes().length == 0;
    }

    /**
     * Create a JsonPatch with a single operation that will add the specified object as JSON
     * to the specified json path
     */
    public JsonPatch addPatch(String path, Object o) {
        return Json.createPatch(Json.createArrayBuilder().add(addPatchOperation(path, o)).build());
    }

    /**
     * Create a JsonPatch operation that will add the specified object as JSON to the specified
     * json path
     */
    public JsonObjectBuilder addPatchOperation(String path, Object o) {
        return Json.createObjectBuilder()
                .add("op", JsonPatch.Operation.ADD.operationName())
                .add("path", path)
                .add("value", toJson(o));
    }

    /**
     * Create a JsonPatch that set all non-null properties of the object at corresponding json paths.
     * E.g. if the object has properties firstName and lastName, calling
     * <code>updatePatch("/person", o)</code> will return a patch with <code>[
     * {op: "add", path: "/person/firstName", value: .... },
     * {op: "add", path: "/person/lastName", value: .... }
     * ]</code>
     */
    public JsonPatch updatePatch(String path, Object o) {
        JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
        for (Method method : o.getClass().getMethods()) {
            if (isGetMethod(method)) {
                try {
                    Object value = method.invoke(o);
                    if (value != null) {
                        patchBuilder.add(path + "/" + getFieldName(method), toJson(value));
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw ExceptionUtil.softenException(e);
                }
            }
        }
        for (Field field : o.getClass().getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                try {
                    Object value = field.get(o);
                    if (value != null) {
                        patchBuilder.add(path + "/" + field.getName(), toJson(value));
                    }
                } catch (IllegalAccessException e) {
                    throw ExceptionUtil.softenException(e);
                }
            }
        }
        return patchBuilder.build();
    }
}

package org.actioncontroller.optional.json;

import javax.json.JsonValue;
import java.util.function.BiFunction;

@FunctionalInterface
public interface ObjectFromJsonMapper<T> extends BiFunction<JsonValue, PojoMapper, T> {
    @Override
    T apply(JsonValue jsonValue, PojoMapper pojoMapper);
}

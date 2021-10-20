package org.actioncontroller;

import org.actioncontroller.exceptions.HttpRequestException;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class TypeConverterFactory {

    private static final Map<Type, Function<String, ?>> converters = Map.of(
            String.class, String::toString,
            Boolean.class, Boolean::parseBoolean,
            Boolean.TYPE, Boolean::parseBoolean,
            URL.class, TypeConverterFactory::toURL,
            URI.class, TypeConverterFactory::toURI,
            UUID.class, UUID::fromString
    );

    private static final Map<Type, Function<String, ?>> numberConverters = Map.of(
            Integer.class, Integer::valueOf,
            Integer.TYPE, Integer::valueOf,
            Long.class, Long::valueOf,
            Long.TYPE, Long::valueOf,
            Short.class, Short::valueOf,
            Short.TYPE, Short::valueOf,
            Double.class, Double::valueOf,
            Double.TYPE, Double::valueOf,
            Float.class, Float::valueOf,
            Float.TYPE, Float::valueOf
    );
    
    private static final Map<Type, Function<String, ?>> dateConverters = Map.of(
            Instant.class, TypeConverterFactory::parseInstant,
            ZonedDateTime.class, s -> parseInstant(s).atZone(ZoneId.systemDefault()),
            OffsetDateTime.class, s -> parseInstant(s).atZone(ZoneId.systemDefault()).toOffsetDateTime(),
            LocalDateTime.class, s -> parseInstant(s).atZone(ZoneId.systemDefault()).toLocalDateTime(),
            LocalDate.class, TypeConverterFactory::parseLocalDate
    );

    private static LocalDate parseLocalDate(String s) {
        if (s.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
            return LocalDate.parse(s);
        } else {
            return parseInstant(s).atZone(ZoneId.systemDefault()).toLocalDate();
        }
    }

    private static Instant parseInstant(String s) {
        if (s.matches("\\d\\d\\d\\d-.*")) {
            return DateTimeFormatter.ISO_DATE_TIME.parse(s, Instant::from);
        } else {
            return DateTimeFormatter.RFC_1123_DATE_TIME.parse(s, Instant::from);
        }
    }

    public static Function<String, ?> fromSingleString(Type targetClass, String description) {
        return nonNullConverter(description, getBaseConverter(targetClass, description));
    }

    public static TypeConverter fromStrings(Type targetClass) {
        return fromStrings(targetClass, "value");
    }

    public static TypeConverter fromStrings(Type targetClass, String description) {
        if (targetClass instanceof ParameterizedType) {
            return fromStringsToParameterizedType((ParameterizedType) targetClass, description);
        } else {
            return nonNullConverter(targetClass, description, getBaseConverter(targetClass, description));
        }
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    private static TypeConverter fromStringsToParameterizedType(ParameterizedType targetClass, String description) {
        Type parameterType = targetClass.getActualTypeArguments()[0];
        if (targetClass.getRawType() == Optional.class) {
            return optionalStringConverter(parameterType, description);
        } else if (targetClass.getRawType() == AtomicReference.class) {
            return atomicReferenceStringConverter(parameterType, description);
        } else if (List.of(Collection.class, List.class, ArrayList.class).contains(targetClass.getRawType())) {
            return arrayListConverter(parameterType, description, getBaseConverter(parameterType, description));
        } else if (List.of(Set.class, HashSet.class).contains(targetClass.getRawType())) {
            return hashSetConverter(parameterType, description, getBaseConverter(parameterType, description));
        } else {
            throw new IllegalArgumentException("Unsupported target " + targetClass + " for " + description);
        }
    }

    private static Function<String, ?> getBaseConverter(Type targetClass, String description) {
        Function<String, ?> converter = converters.get(targetClass);
        if (converter != null) {
            return converter;
        }
        Function<String, ?> numberConverter = numberConverters.get(targetClass);
        if (numberConverter != null) {
            return numberConverter;
        }
        Function<String, ?> dateConverter = dateConverters.get(targetClass);
        if (dateConverter != null) {
            return dateConverter;
        }
        if ((targetClass instanceof Class) && Enum.class.isAssignableFrom((Class<?>) targetClass)) {
            return enumConverter(targetClass);
        }
        throw new IllegalArgumentException("Unsupported target " + targetClass + " for " + description);
    }

    private static Function<String, ?> nonNullConverter(String description, Function<String, ?> converter) {
        return value -> {
            if (value == null) {
                throw new HttpRequestException("Missing required " + description);
            }
            return converter.apply(value);
        };
    }

    private static TypeConverter nonNullConverter(Type targetClass, String description, Function<String, ?> converter) {
        return s -> {
            String value = checkNull(s, description);
            return tryConvert(value, converter, "Cannot convert " + description + " to " + targetClass);
        };
    }

    private static TypeConverter optionalStringConverter(Type parameterType, String description) {
        TypeConverter converter = fromStrings(parameterType, description);
        return s -> {
            if (isEmpty(s)) {
                return Optional.empty();
            }
            return Optional.of(tryConvert(s, converter, "Cannot convert " + description + " to " + parameterType));
        };
    }

    private static TypeConverter atomicReferenceStringConverter(Type parameterType, String description) {
        TypeConverter converter = fromStrings(parameterType, description);
        return s -> {
            if (isEmpty(s)) {
                return new AtomicReference<>();
            }
            try {
                return new AtomicReference<>(converter.apply(s));
            } catch (IllegalArgumentException | DateTimeParseException e) {
                throw new HttpRequestException("Cannot convert " + description + " to " + parameterType + ": " + e.getMessage());
            }
        };
    }

    private static TypeConverter arrayListConverter(Type parameterType, String description, Function<String, ?> baseConverter) {
        return strings -> {
            List<Object> result = new ArrayList<>();
            for (String string : strings) {
                result.add(tryConvert(string, baseConverter, "Cannot convert " + description + " to " + parameterType));
            }
            return result;
        };
    }

    private static TypeConverter hashSetConverter(Type parameterType, String description, Function<String, ?> baseConverter) {
        return strings -> {
            Set<Object> result = new HashSet<>();
            for (String string : strings) {
                result.add(tryConvert(string, baseConverter, "Cannot convert " + description + " to " + parameterType));
            }
            return result;
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> Function<String, T> enumConverter(Type targetClass) {
        T[] enumConstants = ((Class<T>) targetClass).getEnumConstants();
        return s -> {
            for (T enumConstant : enumConstants) {
                if (enumConstant.toString().equals(s)) {
                    return enumConstant;
                }
            }
            throw new IllegalArgumentException(s + " not in " + Arrays.toString(enumConstants));
        };
    }

    private static String checkNull(Collection<String> s, String description) {
        String value = firstValue(s);
        if (value == null) {
            throw new HttpRequestException("Missing required " + description);
        }
        return value;
    }

    private static boolean isEmpty(Collection<String> s) {
        String value = firstValue(s);
        return value == null || value.isEmpty();
    }

    private static String firstValue(Collection<String> s) {
        return s != null && !s.isEmpty() ? s.iterator().next() : null;
    }

    private static Object tryConvert(Collection<String> s, TypeConverter converter, String errorMessage) {
        try {
            return converter.apply(s);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new HttpRequestException(errorMessage + ": " + e.getMessage());
        }
    }

    private static Object tryConvert(String value, Function<String, ?> converter, String errorMessage) {
        try {
            return converter.apply(value);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new HttpRequestException(errorMessage + ": " + e.getMessage());
        }
    }

    private static URI toURI(String s) {
        try {
            return new URI(s);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI " + s);
        }
    }

    private static URL toURL(String s) {
        try {
            return new URL(s);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL " + s);
        }
    }

}

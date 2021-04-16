package org.actioncontroller;

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
import java.util.function.Function;

public class TypeConverterFactory {
    public static final DateTimeFormatter DEFAULT_DATE_FORMAT = DateTimeFormatter.RFC_1123_DATE_TIME;

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
            Short.TYPE, Short::valueOf
    );
    
    private static final Map<Type, Function<String, ?>> dateConverters = Map.of(
            Instant.class, s -> DEFAULT_DATE_FORMAT.parse(s, Instant::from),
            ZonedDateTime.class, s -> DEFAULT_DATE_FORMAT.parse(s, Instant::from).atZone(ZoneId.systemDefault()),
            OffsetDateTime.class, s -> DEFAULT_DATE_FORMAT.parse(s, Instant::from).atZone(ZoneId.systemDefault()).toOffsetDateTime(),
            LocalDateTime.class, s -> DEFAULT_DATE_FORMAT.parse(s, Instant::from).atZone(ZoneId.systemDefault()).toLocalDateTime(),
            LocalDate.class, s -> DEFAULT_DATE_FORMAT.parse(s, Instant::from).atZone(ZoneId.systemDefault()).toLocalDate()
    );

    public static Function<List<String>, ?> fromStrings(Type targetClass) {
        return fromStrings(targetClass, "value");
    }

    public static Function<List<String>, ?> fromStrings(Type targetClass, String description) {
        if (targetClass instanceof ParameterizedType) {
            return fromStringsToParameterizedType((ParameterizedType) targetClass, description);
        }
        return nonNullConverter(targetClass, description, getBaseConverter(targetClass, description));
    }

    @SuppressWarnings("unchecked")
    private static <T> Function<String, T> getBaseConverter(Type targetClass, String description) {
        Function<String, ?> converter = converters.get(targetClass);
        if (converter != null) {
            return (Function<String, T>) converter;
        }
        Function<String, ?> numberConverter = numberConverters.get(targetClass);
        if (numberConverter != null) {
            return (Function<String, T>) numberConverter;
        }
        Function<String, ?> dateConverter = dateConverters.get(targetClass);
        if (dateConverter != null) {
            return (Function<String, T>) dateConverter;
        }
        if ((targetClass instanceof Class) && Enum.class.isAssignableFrom((Class<?>) targetClass)) {
            return enumConverter(targetClass);
        }
        throw new IllegalArgumentException("Unsupported target " + targetClass + " for " + description);
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    private static Function<List<String>, Object> fromStringsToParameterizedType(ParameterizedType targetClass, String description) {
        Type parameterType = targetClass.getActualTypeArguments()[0];
        if (targetClass.getRawType() == Optional.class) {
            Function<List<String>, ?> baseConverter = fromStrings(parameterType, description);
            return optionalStringConverter(description, parameterType, baseConverter);
        } else if (List.of(Collection.class, List.class, ArrayList.class).contains(targetClass.getRawType())) {
            return arrayListConverter(parameterType, description, getBaseConverter(parameterType, description));
        } else if (List.of(Set.class, HashSet.class).contains(targetClass.getRawType())) {
            return hashSetConverter(parameterType, description, getBaseConverter(parameterType, description));
        } else {
            throw new IllegalArgumentException("Unsupported target " + targetClass + " for " + description);
        }
    }

    private static Function<List<String>, ?> nonNullConverter(Type targetClass, String description, Function<String, ?> converter) {
        return s -> {
            if (s == null || s.isEmpty() || s.get(0) == null) {
                throw new HttpRequestException("Missing " + description);
            }
            try {
                return converter.apply(s.get(0));
            } catch (IllegalArgumentException | DateTimeParseException e) {
                throw new HttpRequestException("Cannot convert " + description + " to " + targetClass + ": " + e.getMessage());
            }
        };
    }

    private static Function<List<String>, Object> optionalStringConverter(String description, Type parameterType, Function<List<String>, ?> converter) {
        return s -> {
            if (s == null || s.isEmpty() || s.get(0) == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(converter.apply(s));
            } catch (IllegalArgumentException | DateTimeParseException e) {
                throw new HttpRequestException("Cannot convert " + description + " to " + parameterType + ": " + e.getMessage());
            }
        };
    }

    private static Function<List<String>, Object> arrayListConverter(Type parameterType, String description, Function<String, Object> baseConverter) {
        return strings -> {
            ArrayList<Object> result = new ArrayList<>();
            for (String string : strings) {
                try {
                    result.add(baseConverter.apply(string));
                } catch (IllegalArgumentException | DateTimeParseException e) {
                    throw new HttpRequestException("Cannot convert " + description + " to " + parameterType + ": " + e.getMessage());
                }
            }
            return result;
        };
    }

    private static Function<List<String>, Object> hashSetConverter(Type parameterType, String description, Function<String, Object> baseConverter) {
        return strings -> {
            HashSet<Object> result = new HashSet<>();
            for (String string : strings) {
                try {
                    result.add(baseConverter.apply(string));
                } catch (IllegalArgumentException | DateTimeParseException e) {
                    throw new HttpRequestException("Cannot convert " + description + " to " + parameterType + ": " + e.getMessage());
                }
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

package org.actioncontroller;

import org.actioncontroller.exceptions.HttpRequestException;
import org.junit.Test;

import java.lang.annotation.RetentionPolicy;
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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TypeConverterFactoryTest {
    
    @Test
    public void shouldConvertStringsToFirstString() {
        assertThat(TypeConverterFactory.fromStrings(String.class).apply(List.of("Hello World")))
                .isEqualTo("Hello World");
    }
    
    @Test
    public void shouldConvertStringsToFirstNumber() {
        assertThat(TypeConverterFactory.fromStrings(Integer.class).apply(List.of("1001")))
                .isEqualTo(1001);
        assertThat(TypeConverterFactory.fromStrings(Long.class).apply(List.of("1001")))
                .isEqualTo(1001L);
    }
    
    @Test
    public void shouldConvertStringsToDateTypes() {
        Instant instant = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String asString = TypeConverterFactory.DEFAULT_DATE_FORMAT.format(instant.atOffset(ZoneOffset.UTC));
        assertThat(TypeConverterFactory.fromStrings(Instant.class).apply(List.of(asString))).isEqualTo(instant);
        assertThat(TypeConverterFactory.fromStrings(ZonedDateTime.class).apply(List.of(asString)))
                .isEqualTo(instant.atZone(ZoneId.systemDefault()));
        assertThat(TypeConverterFactory.fromStrings(OffsetDateTime.class).apply(List.of(asString)))
                .isEqualTo(instant.atZone(ZoneId.systemDefault()).toOffsetDateTime());
        assertThat(TypeConverterFactory.fromStrings(LocalDateTime.class).apply(List.of(asString)))
                .isEqualTo(instant.atZone(ZoneId.systemDefault()).toLocalDateTime());
        assertThat(TypeConverterFactory.fromStrings(LocalDate.class).apply(List.of(asString)))
                .isEqualTo(instant.atZone(ZoneId.systemDefault()).toLocalDate());
    }
    
    @Test
    public void shouldConvertStringsToStringyTypes() throws MalformedURLException, URISyntaxException {
        assertThat(TypeConverterFactory.fromStrings(URL.class).apply(List.of("https://example.com:8080/foo")))
                .isEqualTo(new URL("https://example.com:8080/foo"));
        assertThat(TypeConverterFactory.fromStrings(URI.class).apply(List.of("https://example.com:8080/foo")))
                .isEqualTo(new URI("https://example.com:8080/foo"));
        UUID uuid = UUID.randomUUID();
        assertThat(TypeConverterFactory.fromStrings(UUID.class).apply(List.of(uuid.toString()))).isEqualTo(uuid);
    }
    
    @Test
    public void shouldConvertStringsToEnumTypes() {
        assertThat(TypeConverterFactory.fromStrings(RetentionPolicy.class).apply(List.of("RUNTIME")))
                .isEqualTo(RetentionPolicy.RUNTIME);
        assertThatThrownBy(() -> TypeConverterFactory.fromStrings(RetentionPolicy.class).apply(List.of("SILLY")))
                .isInstanceOf(HttpRequestException.class)
                .hasMessage("Cannot convert value to class java.lang.annotation.RetentionPolicy: SILLY not in [SOURCE, CLASS, RUNTIME]");
    }
    
    @Test
    public void shouldThrowExceptionOnIllegalValues() {
        assertThatThrownBy(() -> TypeConverterFactory.fromStrings(URL.class, "parameter myUrl").apply(List.of("Hello James")))
                .isInstanceOf(HttpRequestException.class)
                .hasMessage("Cannot convert parameter myUrl to class java.net.URL: Invalid URL Hello James");
        assertThatThrownBy(() -> TypeConverterFactory.fromStrings(Instant.class).apply(List.of("Hello James")))
                .isInstanceOf(HttpRequestException.class)
                .hasMessage("Cannot convert value to class java.time.Instant: Text 'Hello James' could not be parsed at index 0");
        assertThatThrownBy(() -> TypeConverterFactory.fromStrings(Long.TYPE).apply(List.of("Hello James")))
                .isInstanceOf(HttpRequestException.class)
                .hasMessage("Cannot convert value to long: For input string: \"Hello James\"");
    }
    
    @Test
    public void shouldThrowOnUnsupportedType() {
        assertThatThrownBy(() -> TypeConverterFactory.fromStrings(Object.class, "parameter id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported target class java.lang.Object for parameter id");
    }

    @Test
    public void shouldThrowOnMissingValues() {
        assertThatThrownBy(() -> TypeConverterFactory.fromStrings(URL.class, "parameter myUrl").apply(null))
                .isInstanceOf(HttpRequestException.class)
                .hasMessage("Missing required parameter myUrl");
        assertThatThrownBy(() -> TypeConverterFactory.fromStrings(double.class).apply(List.of()))
                .isInstanceOf(HttpRequestException.class)
                .hasMessage("Missing required value");
        assertThatThrownBy(() -> TypeConverterFactory.fromStrings(Enum.class).apply(Collections.singletonList(null)))
                .isInstanceOf(HttpRequestException.class)
                .hasMessage("Missing required value");
    }
    
    @Test
    public void shouldReturnOptionalValues() {
        assertThat(TypeConverterFactory.fromStrings(parameterized(Optional.class, String.class)).apply(List.of("hello")))
                .isEqualTo(Optional.of("hello"));
        assertThat(TypeConverterFactory.fromStrings(parameterized(Optional.class, Instant.class)).apply(null))
                .isEqualTo(Optional.empty());
        assertThatThrownBy(() -> TypeConverterFactory.fromStrings(parameterized(Optional.class, Short.TYPE)).apply(List.of("100000")))
                .hasMessage("Cannot convert value to short: Value out of range. Value:\"100000\" Radix:10");
    }
    
    @Test
    public void shouldReturnCollectionValues() {
        assertThat(TypeConverterFactory.fromStrings(parameterized(List.class, Boolean.TYPE)).apply(List.of("true", "false", "false")))
                .isEqualTo(List.of(true, false, false));
        assertThat(TypeConverterFactory.fromStrings(parameterized(Set.class, String.class)).apply(List.of("a", "b", "b")))
                .isEqualTo(Set.of("a", "b"));
        TypeConverter optionalIntListConverter = TypeConverterFactory.fromStrings(
                parameterized(Optional.class, parameterized(List.class, Integer.TYPE))
        );
        assertThat(optionalIntListConverter.apply(List.of("9", "8", "7")))
                .isEqualTo(Optional.of(List.of(9, 8, 7)));
        assertThat(optionalIntListConverter.apply(List.of()))
                .isEqualTo(Optional.empty());
    }

    private ParameterizedType parameterized(Class<?> containerClass, Type innerClass) {
        return new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
                return new Type[] { innerClass };
            }

            @Override
            public Type getRawType() {
                return containerClass;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }

            @Override
            public String toString() {
                return containerClass.getSimpleName() + "<" + innerClass + ">";
            }
        };
    }
}
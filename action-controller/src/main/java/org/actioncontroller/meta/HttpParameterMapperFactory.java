package org.actioncontroller.meta;

import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.ExceptionUtil;
import org.actioncontroller.TypesUtil;
import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.client.ApiClientExchange;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Implementations of this interface should be annotated on annotations used to
 * convert HTTP requests into method invocation arguments, such as
 * {@link org.actioncontroller.RequestParam}, {@link org.actioncontroller.PathParam}
 * and {@link org.actioncontroller.UnencryptedCookie}
 */
public interface HttpParameterMapperFactory<ANNOTATION extends Annotation> extends AnnotationFactory {

    static Optional<HttpParameterMapper> createNewInstance(Parameter parameter, ApiControllerContext context) {
        return AnnotationFactory.getAnnotatedAnnotation(parameter, HttpParameterMapping.class)
                .map(annotation -> createFactory(annotation).safeCreate(annotation, parameter, context));
    }

    static Optional<HttpClientParameterMapper> createNewClientInstance(Parameter parameter) {
        return AnnotationFactory.getAnnotatedAnnotation(parameter, HttpParameterMapping.class)
                .map(annotation -> createFactory(annotation).clientParameterMapper(annotation, parameter));
    }

    static <T extends Annotation> HttpParameterMapperFactory<T> createFactory(T annotation) {
        //noinspection unchecked
        return AnnotationFactory.newInstance(annotation.annotationType().getAnnotation(HttpParameterMapping.class).value());
    }

    /**
     * Helper method for creating a {@link HttpParameterMapper} based on a parameterType that may
     * be {@link Optional}, {@link Consumer}, {@link AtomicReference} or any other object.
     * Optionals are used to specify optional values, Consumers are used to specify values that
     * should be returned from the action method and AtomicReference are used to specify values
     * that should both be passed to the action method and returned to the client afterwards.
     *
     * @param parameterType May be Optional, Consumer, AtomicReference or other type
     * @param getter Will be called on entry unless parameterType is a Consumer
     * @param setter Will be called when request is complete for Consumer and AtomicReference
     * @param defaultValue If parameterType is not Optional and the getter returned Optional.empty,
     *                     default value will be called to determine the value
     */
    static HttpParameterMapper createMapper(
            Type parameterType,
            BiFunction<ApiHttpExchange, Class<?>, Optional<Object>> getter,
            BiConsumer<ApiHttpExchange, Object> setter,
            Supplier<Object> defaultValue
    ) {
        Class<?> type = TypesUtil.getRawType(parameterType);
        boolean optional = type == Optional.class;
        if (type == Consumer.class) {
            return exchange -> (Consumer<?>) o -> setter.accept(exchange, o);
        } else if (type == AtomicReference.class) {
            Class<?> typeParameter = TypesUtil.typeParameter(parameterType);
            return new HttpParameterMapper() {
                @Override
                public Object apply(ApiHttpExchange exchange) {
                    return new AtomicReference<>(getter.apply(exchange, typeParameter).orElse(null));
                }

                @Override
                public void onComplete(ApiHttpExchange exchange, Object argument) {
                    setter.accept(exchange, ((AtomicReference<?>) argument).get());
                }
            };
        } else if (optional) {
            Class<?> typeParameter = TypesUtil.typeParameter(parameterType);
            return exchange -> getter.apply(exchange, typeParameter);
        } else {
            return exchange -> getter.apply(exchange, type).orElseGet(defaultValue);
        }
    }

    /**
     * Helper method for creating a {@link HttpParameterMapper} based on a parameterType that may
     * be {@link Consumer}, {@link AtomicReference} or any other object. Consumers are used to specify values
     * that should be set after the request completes. AtomicReference are used to specify values
     * that should both be passed to the request method and returned after the request completes.
     *
     * @param parameterType May be Optional, Consumer, AtomicReference or other type
     * @param exchangeWriter Will be called to populate the request
     * @param exchangeReader Will be called to retrieve values to Consumer or AtomicReference
     *                       after request completes
     */
    static HttpClientParameterMapper createClientMapper(
            Type parameterType,
            BiConsumer<ApiClientExchange, Object> exchangeWriter,
            BiFunction<ApiClientExchange, Class<?>, Optional<Object>> exchangeReader
    ) {
        Class<?> type = TypesUtil.getRawType(parameterType);
        if (type == Consumer.class) {
            Class<?> typeParameter = TypesUtil.typeParameter(parameterType);
            return (exchange, arg) -> {
                if (arg != null) {
                    exchangeReader.apply(exchange, typeParameter)
                            .ifPresent(((Consumer) arg)::accept);
                }
            };
        } else if (type == AtomicReference.class) {
            Class<?> typeParameter = TypesUtil.typeParameter(parameterType);
            return (exchange, arg) -> {
                if (arg != null) {
                    exchangeWriter.accept(exchange, ((AtomicReference<?>) arg).get());
                    exchangeReader.apply(exchange, typeParameter)
                            .ifPresent(((AtomicReference) arg)::set);
                }
            };
        } else {
            return exchangeWriter::accept;
        }
    }

    /**
     * Create a mapper to convert a {@link ApiHttpExchange} into method call arguments.
     */
    HttpParameterMapper create(ANNOTATION annotation, Parameter parameter, ApiControllerContext context) throws Exception;

    default HttpParameterMapper safeCreate(ANNOTATION annotation, Parameter parameter, ApiControllerContext context) {
        try {
            return create(annotation, parameter, context);
        } catch (Exception e) {
            throw ExceptionUtil.softenException(e);
        }
    }

    /**
     * Used by {@link ApiClientClassProxy} to convert
     * method arguments into HTTP request information.
     */
    default HttpClientParameterMapper clientParameterMapper(ANNOTATION annotation, Parameter parameter) {
        throw new UnsupportedOperationException(getClass() + " does not support " + HttpClientReturnMapper.class.getName());
    }



}

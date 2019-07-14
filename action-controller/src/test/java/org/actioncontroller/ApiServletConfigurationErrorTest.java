package org.actioncontroller;

import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpRequestParameterMapping;
import org.actioncontroller.meta.HttpRequestParameterMappingFactory;
import org.actioncontroller.meta.HttpReturnMapperFactory;
import org.actioncontroller.meta.HttpReturnMapping;
import org.actioncontroller.meta.HttpReturnValueMapping;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.extend.junit.ExpectedLogEventsRule;
import org.slf4j.event.Level;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.reflect.Parameter;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ApiServletConfigurationErrorTest {

    @Rule
    public ExpectedLogEventsRule expectedLogEventsRule = new ExpectedLogEventsRule(Level.WARN);

    @Test
    public void shouldReportAllActionErrors() {
        ApiServlet apiServlet = new ApiServlet() {
            @Override
            public void init() {
                registerController(new ControllerWithErrors());
                registerController(new OtherControllerWithErrors());
                registerController(new ControllerWithMismatchedPathParams());
            }
        };

        assertThatThrownBy(() -> apiServlet.init(null))
                .isInstanceOf(ApiServletException.class)
                .hasMessageContaining(ControllerWithErrors.class.getName())
                .hasMessageContaining("actionWithUnboundParameter")
                .hasMessageContaining("actionWithUnknownReturnType")
                .hasMessageContaining(OtherControllerWithErrors.class.getName())
                .hasMessageContaining("actionWithInvalidMappingAnnotation")
                .hasMessageContaining(ControllerWithMismatchedPathParams.class.getName())
                .hasMessageContaining("incorrect")
        ;

        expectedLogEventsRule.expectPattern(ApiServletAction.class, Level.WARN, "Failed to setup {}");
        expectedLogEventsRule.expect(ApiServletAction.class, Level.WARN,
                "Unused path parameters for ControllerWithMismatchedPathParams.actionWithParameterMismatch(String): [myTest]");
    }


    public static class ParameterMappingWithoutProperConstructor implements
            HttpRequestParameterMappingFactory<Annotation>,
            HttpReturnMapperFactory<Annotation>
    {

        public ParameterMappingWithoutProperConstructor(@SuppressWarnings("unused") String string) {
        }

        @Override
        public HttpRequestParameterMapping create(Annotation annotation, Parameter parameter) {
            return null;
        }

        @Override
        public HttpReturnValueMapping create(Annotation annotation, Class<?> returnType) {
            return null;
        }
    }


    @Retention(RUNTIME)
    @HttpParameterMapping(ParameterMappingWithoutProperConstructor.class)
    @HttpReturnMapping(ParameterMappingWithoutProperConstructor.class)
    public @interface CustomAnnotation {

    }


    private class ControllerWithErrors {

        @Get("/")
        public void actionWithUnboundParameter(@SuppressWarnings("unused") String parameter) {

        }

        @Post("/")
        public Object actionWithUnknownReturnType() {
            return null;
        }

    }

    private class ControllerWithMismatchedPathParams {
        @Get("/test/:myTest")
        public void actionWithParameterMismatch(@PathParam("incorrect") String param) {

        }
    }


    private class OtherControllerWithErrors {

        @Get("/foo")
        public String actionWithInvalidMappingAnnotation(
                @CustomAnnotation String parameter
        ) {
            return parameter;
        }

        @Get("/bar")
        @CustomAnnotation
        public String actionWithInvalidReturnAnnotation() {
            return "";
        }

    }



}

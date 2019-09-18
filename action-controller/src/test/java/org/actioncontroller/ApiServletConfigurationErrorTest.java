package org.actioncontroller;

import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpReturnMapperFactory;
import org.actioncontroller.meta.HttpReturnMapping;
import org.actioncontroller.meta.HttpReturnMapper;
import org.actioncontroller.servlet.ApiServlet;
import org.actioncontroller.servlet.ApiServletException;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.extend.junit.ExpectedLogEventsRule;
import org.slf4j.event.Level;

import javax.servlet.ServletException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.reflect.Parameter;
import java.math.BigInteger;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ApiServletConfigurationErrorTest {

    @Rule
    public ExpectedLogEventsRule expectedLogEventsRule = new ExpectedLogEventsRule(Level.WARN);

    @Test
    public void shouldReportAllActionErrors() {
        expectedLogEventsRule.expectPattern(ApiControllerAction.class, Level.WARN, "Unused path parameters for {}: {}");
        expectedLogEventsRule.expectPattern(ApiControllerAction.class, Level.WARN, "Failed to setup {}");
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

        expectedLogEventsRule.expectPattern(ApiControllerAction.class, Level.WARN, "Failed to setup {}");
        expectedLogEventsRule.expect(ApiControllerAction.class, Level.WARN,
                "Unused path parameters for ControllerWithMismatchedPathParams.actionWithParameterMismatch(String): [myTest]");
    }

    @Test
    public void shouldReportErrorForServletWithNoControllers() {
        ApiServlet apiServlet = new ApiServlet();
        assertThatThrownBy(() -> apiServlet.init(null))
                .isInstanceOf(ActionControllerConfigurationException.class)
                .hasMessageContaining("no controllers");
    }

    @Test
    public void shouldReportErrorForControllerWithNoActions() {
        Object controller = new BigInteger("100");
        assertThatThrownBy(() -> new ApiServlet(controller))
                .isInstanceOf(ActionControllerConfigurationException.class)
                .hasMessageContaining("no actions")
                .hasMessageContaining(controller.toString());
    }


    public static class ParameterMapperWithoutProperConstructor implements
            HttpParameterMapperFactory<Annotation>,
            HttpReturnMapperFactory<Annotation>
    {

        public ParameterMapperWithoutProperConstructor(@SuppressWarnings("unused") String string) {
        }

        @Override
        public HttpParameterMapper create(Annotation annotation, Parameter parameter) {
            return null;
        }

        @Override
        public HttpReturnMapper create(Annotation annotation, Class<?> returnType) {
            return null;
        }
    }


    @Retention(RUNTIME)
    @HttpParameterMapping(ParameterMapperWithoutProperConstructor.class)
    @HttpReturnMapping(ParameterMapperWithoutProperConstructor.class)
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

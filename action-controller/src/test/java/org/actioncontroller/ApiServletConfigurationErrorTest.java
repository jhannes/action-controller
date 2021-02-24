package org.actioncontroller;

import org.actioncontroller.client.ApiClientClassProxy;
import org.actioncontroller.client.HttpClientException;
import org.actioncontroller.meta.HttpClientParameterMapper;
import org.actioncontroller.meta.HttpParameterMapping;
import org.actioncontroller.meta.HttpParameterMapper;
import org.actioncontroller.meta.HttpParameterMapperFactory;
import org.actioncontroller.meta.HttpReturnMapperFactory;
import org.actioncontroller.meta.HttpReturnMapping;
import org.actioncontroller.meta.HttpReturnMapper;
import org.actioncontroller.servlet.ApiServlet;
import org.actioncontroller.servlet.ActionControllerConfigurationException;
import org.actioncontroller.test.FakeApiClient;
import org.junit.Rule;
import org.junit.Test;
import org.logevents.extend.junit.ExpectedLogEventsRule;
import org.slf4j.event.Level;

import javax.servlet.ServletException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ApiServletConfigurationErrorTest {

    @Rule
    public ExpectedLogEventsRule expectedLogEventsRule = new ExpectedLogEventsRule(Level.WARN);

    @Test
    public void shouldReportAllActionErrors() {
        expectedLogEventsRule.expectPattern(ApiControllerAction.class, Level.WARN, "Unused path parameters for {}: {}");
        expectedLogEventsRule.expectPattern(ApiControllerActionRouter.class, Level.ERROR, "Failed to setup {}");
        ApiServlet apiServlet = new ApiServlet(List.of(
                new ControllerWithErrors(), new OtherControllerWithErrors(), new ControllerWithMismatchedPathParams())
        );

        assertThatThrownBy(() -> apiServlet.init(null))
                .isInstanceOf(ActionControllerConfigurationException.class)
                .hasMessageContaining(ControllerWithErrors.class.getName())
                .hasMessageContaining("actionWithUnboundParameter")
                .hasMessageContaining("actionWithUnknownReturnType")
                .hasMessageContaining(OtherControllerWithErrors.class.getName())
                .hasMessageContaining("actionWithInvalidMappingAnnotation")
                .hasMessageContaining(ControllerWithMismatchedPathParams.class.getName())
                .hasMessageContaining("incorrect");

        expectedLogEventsRule.expect(ApiControllerAction.class, Level.WARN,
                "Unused path parameters for ControllerWithMismatchedPathParams.actionWithParameterMismatch(String): [myTest]");
    }

    @Test
    public void shouldReportParameterizedRoutesWithConflicts() {
        ControllerWithOverlappingPathParameters controller = new ControllerWithOverlappingPathParameters();
        assertThatThrownBy(() -> new ApiServlet(controller).init(null))
                .isInstanceOf(ActionControllerConfigurationException.class)
                .hasMessageContaining("is in conflict with")
                .hasMessageContaining("/files/{otherName}")
                .hasMessageContaining("/files/{filename}")
                .hasMessageContaining(controller.getClass().getSimpleName());
    }

    @Test
    public void shouldReportRoutesWithPathConflicts() {
        ControllerWithOverlappingPath controller = new ControllerWithOverlappingPath();
        assertThatThrownBy(() -> new ApiServlet(controller).init(null))
                .isInstanceOf(ActionControllerConfigurationException.class)
                .hasMessageContaining("is in conflict with")
                .hasMessageContaining("doActionA")
                .hasMessageContaining("doActionB")
                .hasMessageContaining(controller.getClass().getSimpleName());
    }

    @Test
    public void shouldReportRoutesWithConflicts() {
        ControllerWithOverlappingPaths controller = new ControllerWithOverlappingPaths();
        assertThatThrownBy(() -> new ApiServlet(controller).init(null))
                .isInstanceOf(ActionControllerConfigurationException.class)
                .hasMessageContaining("is in conflict with")
                .hasMessageContaining("/files/{filename}")
                .hasMessageContaining("/files/{filename}")
                .hasMessageContaining(controller.getClass().getSimpleName());
    }

    @Test
    public void shouldReportQueryParametersWithConflicts() {
        ControllerWithSameQuery controller = new ControllerWithSameQuery();
        assertThatThrownBy(() -> new ApiServlet(controller).init(null))
                .isInstanceOf(ActionControllerConfigurationException.class)
                .hasMessageContaining("is in conflict with")
                .hasMessageContaining("/files?extension")
                .hasMessageContaining(controller.getClass().getSimpleName());
    }

    @Test
    public void shouldReportRoutesWithConflictingPatterns() {
        ControllerWithOverlappingPathExpressions controller = new ControllerWithOverlappingPathExpressions();
        assertThatThrownBy(() -> new ApiServlet(controller).init(null))
                .isInstanceOf(ActionControllerConfigurationException.class)
                .hasMessageContaining("is in conflict with")
                .hasMessageContaining("/files/{filename}.html")
                .hasMessageContaining("/files/{otherName}.html")
                .hasMessageContaining(controller.getClass().getSimpleName());
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
        ApiServlet apiServlet = new ApiServlet(controller);
        assertThatThrownBy(() -> apiServlet.init(null))
                .isInstanceOf(ActionControllerConfigurationException.class)
                .hasMessageContaining("no actions")
                .hasMessageContaining(controller.toString());
    }

    @Test
    public void shouldReportErrorOnRedirectWithBothValueAndReturn() {
        expectedLogEventsRule.expectPattern(ApiControllerActionRouter.class, Level.ERROR, "Failed to setup {}");
        ApiServlet servlet = new ApiServlet(new ControllerWithInvalidRedirect());
        assertThatThrownBy(() -> servlet.init(null))
                .isInstanceOf(ActionControllerConfigurationException.class)
                .hasMessageContaining("with value(), return value must be void");
    }
    
    @Test
    public void shouldAddControllerMethodToStackTraceOnServerError() throws ServletException, MalformedURLException {
        ApiServlet servlet = new ApiServlet(new ControllerWithRuntimeError());
        servlet.init(null);

        ControllerWithRuntimeError clientController = ApiClientClassProxy.create(ControllerWithRuntimeError.class, new FakeApiClient(new URL("http://example.org"), "/", servlet));
        expectedLogEventsRule.expectMatch(expect -> {
            if (expect.getEvent().getLevel() == Level.WARN && expect.getEvent().getLoggerName().equals(ApiControllerAction.class.getName())) {
                assertThat(expect.getEvent().getThrowable().getStackTrace()[0].getClassName()).isEqualTo(ControllerWithRuntimeError.class.getName());
                assertThat(expect.getEvent().getThrowable().getStackTrace()[0].getMethodName()).isEqualTo("doIt");
                expect.pattern("While processing {} arguments to {}");
            }
        });
        assertThatThrownBy(() -> clientController.doIt("something")).isInstanceOf(HttpClientException.class);
    }

    public static class ParameterMapperWithoutProperConstructor implements
            HttpParameterMapperFactory<Annotation>,
            HttpReturnMapperFactory<Annotation>
    {

        public ParameterMapperWithoutProperConstructor(@SuppressWarnings("unused") String string) {
        }

        @Override
        public HttpParameterMapper create(Annotation annotation, Parameter parameter, ApiControllerContext context) {
            return null;
        }

        @Override
        public HttpReturnMapper create(Annotation annotation, Type returnType) {
            return null;
        }
    }


    @Retention(RUNTIME)
    @HttpParameterMapping(ParameterMapperWithoutProperConstructor.class)
    @HttpReturnMapping(ParameterMapperWithoutProperConstructor.class)
    public @interface CustomAnnotation {

    }

    private static class OtherControllerWithErrors {

        @GET("/foo")
        public String actionWithInvalidMappingAnnotation(
                @CustomAnnotation String parameter
        ) {
            return parameter;
        }

        @GET("/bar")
        @CustomAnnotation
        public String actionWithInvalidReturnAnnotation() {
            return "";
        }

    }

    @Retention(RUNTIME)
    @HttpParameterMapping(ThrowServerError.Mapper.class)
    public @interface ThrowServerError {

        class Mapper implements HttpParameterMapperFactory<ThrowServerError> {
            @Override
            public HttpParameterMapper create(ThrowServerError annotation, Parameter parameter, ApiControllerContext context) throws Exception {
                return exchange -> {
                    throw new HttpServerErrorException("Something went wrong");
                };
            }

            @Override
            public HttpClientParameterMapper clientParameterMapper(ThrowServerError annotation, Parameter parameter) {
                return ((exchange, arg) -> {});
            }
        }
    }

    public static class ControllerWithRuntimeError {
        @GET("/")
        @SendRedirect("/")
        public void doIt(@ThrowServerError String foo) {
            
        }
    }


    private static class ControllerWithErrors {

        @GET("/")
        public void actionWithUnboundParameter(@SuppressWarnings("unused") String parameter) {

        }

        @POST("/")
        public Object actionWithUnknownReturnType() {
            return null;
        }

    }

    private static class ControllerWithMismatchedPathParams {
        @GET("/test/:myTest")
        public void actionWithParameterMismatch(@PathParam("incorrect") String param) {

        }
    }



    private static class ControllerWithInvalidRedirect {
        @GET("/redirector")
        @SendRedirect("/")
        public String redirect() {
            return "/invalidToReturnWithParameter";
        }
    }

    private static class ControllerWithOverlappingPathParameters {
        @POST("/files/{filename}")
        public void doAction(@PathParam("filename") String filename) {}

        @POST("/files/{otherName}")
        public void doOtherAction(@PathParam("otherName") String otherName) {}
    }

    private static class ControllerWithOverlappingPath {
        @POST("/")
        public void doActionA() {}

        @POST("/")
        public void doActionB() {}
    }

    private static class ControllerWithOverlappingPathExpressions {
        @POST("/files/{filename}.html")
        public void doAction(@PathParam("filename") String filename) {}

        @POST("/files/{otherName}.html")
        public void doOtherAction(@PathParam("otherName") String otherName) {}
    }

    private static class ControllerWithOverlappingPaths {
        @POST("/files/{filename}")
        public void doAction(@PathParam("filename") String filename) {}

        @POST("/files/{file}")
        public void doOtherAction(@PathParam("file") String filename) {}
    }

    private static class ControllerWithSameQuery {
        @POST("/files?extension")
        public void doAction() {}

        @POST("/files?extension")
        public void doOtherAction() {}
    }
}

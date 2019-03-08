package org.actioncontroller;

import org.actioncontroller.meta.HttpResponseValueMapping;
import org.actioncontroller.meta.HttpReturnMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target(METHOD)
@HttpReturnMapping(HttpResponseHeader.Mapping.class)
public @interface HttpResponseHeader {
    String value();

    public class Mapping implements HttpResponseValueMapping {
        private HttpResponseHeader annotation;

        public Mapping(HttpResponseHeader annotation, Class<?> returnType) {
            this.annotation = annotation;
        }

        @Override
        public void accept(Object result, HttpServletResponse resp, HttpServletRequest req) throws IOException {
            resp.setHeader(annotation.value(), result.toString());
        }
    }
}


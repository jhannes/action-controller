package org.actioncontroller;

import org.actioncontroller.meta.HttpResponseValueMapping;
import org.actioncontroller.meta.HttpReturnMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.net.URL;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target(METHOD)
@HttpReturnMapping(ContentLocationHeader.Mapping.class)
public @interface ContentLocationHeader {

    public class Mapping implements HttpResponseValueMapping {
        private ContentLocationHeader annotation;

        public Mapping(ContentLocationHeader annotation, Class<?> returnType) {
            this.annotation = annotation;
        }

        @Override
        public void accept(Object result, HttpServletResponse resp, HttpServletRequest req) throws IOException {
            resp.setHeader("Content-Location", new URL(new URL(req.getRequestURL().toString()), result.toString()).toString());
        }
    }
}


package org.actioncontroller;

import org.actioncontroller.meta.HttpResponseValueMapping;
import org.actioncontroller.meta.HttpReturnMapping;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target(METHOD)
@HttpReturnMapping(SendRedirectMapping.class)
public @interface SendRedirect {

}

class SendRedirectMapping implements HttpResponseValueMapping {
    @Override
    public void accept(Object result, HttpServletResponse resp) throws IOException {
        resp.sendRedirect(result.toString());
    }
}
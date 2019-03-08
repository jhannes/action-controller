package org.actioncontroller.meta;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@FunctionalInterface
public interface HttpResponseValueMapping {

    void accept(Object result, HttpServletResponse resp, HttpServletRequest req) throws IOException;


}

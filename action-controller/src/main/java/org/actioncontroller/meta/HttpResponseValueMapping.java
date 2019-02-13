package org.actioncontroller.meta;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@FunctionalInterface
public interface HttpResponseValueMapping {

    void accept(Object result, HttpServletResponse resp) throws IOException;


}

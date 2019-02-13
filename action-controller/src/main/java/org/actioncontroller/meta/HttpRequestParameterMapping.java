package org.actioncontroller.meta;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;

@FunctionalInterface
public interface HttpRequestParameterMapping {

    Object apply(HttpServletRequest req, Map<String, String> pathParameters) throws IOException;

}
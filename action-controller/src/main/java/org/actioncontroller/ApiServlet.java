package org.actioncontroller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiServlet extends HttpServlet {

    private static Logger logger = LoggerFactory.getLogger(ApiServlet.class);

    protected boolean isUserLoggedIn(HttpServletRequest req) {
        return req.getRemoteUser() != null;
    }

    protected boolean isUserInRole(HttpServletRequest req, String role) {
        return req.isUserInRole(role);
    }

    private Map<String, List<ApiServletAction>> routes = new HashMap<>();
    {
        routes.put("GET", new ArrayList<>());
        routes.put("POST", new ArrayList<>());
        routes.put("PUT", new ArrayList<>());
        routes.put("DELETE", new ArrayList<>());
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Map<String, String> pathParameters = new HashMap<>();
        for (ApiServletAction apiRoute : routes.get(req.getMethod())) {
            if (apiRoute.matches(req.getPathInfo(), pathParameters)) {
                apiRoute.invoke(req, resp, pathParameters, this);
                return;
            }
        }

        logger.warn("No route for {}", req.getPathInfo());
        resp.sendError(404);
    }

    // TODO: Collect all validation errors
    protected void registerController(Object controller) {
        registerActions(controller);
    }

    private void registerActions(Object controller) {
        for (Method method : controller.getClass().getMethods()) {
            Get getAnnotation = method.getAnnotation(Get.class);
            if (getAnnotation != null) {
                routes.get("GET").add(new ApiServletAction(controller, method, getAnnotation.value()));
            }
            Post postAnnotation = method.getAnnotation(Post.class);
            if (postAnnotation != null) {
                routes.get("POST").add(new ApiServletAction(controller, method, postAnnotation.value()));
            }
            Put putAnnotation = method.getAnnotation(Put.class);
            if (putAnnotation != null) {
                routes.get("PUT").add(new ApiServletAction(controller, method, putAnnotation.value()));
            }
            Delete deleteAnnotation = method.getAnnotation(Delete.class);
            if (deleteAnnotation != null) {
                routes.get("DELETE").add(new ApiServletAction(controller, method, deleteAnnotation.value()));
            }
        }
    }
}

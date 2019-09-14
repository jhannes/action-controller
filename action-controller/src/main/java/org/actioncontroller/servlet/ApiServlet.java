package org.actioncontroller.servlet;

import org.actioncontroller.ApiControllerAction;
import org.actioncontroller.ApiControllerMethodAction;
import org.actioncontroller.ApiControllerCompositeException;
import org.actioncontroller.HttpActionException;
import org.actioncontroller.UserContext;
import org.actioncontroller.meta.ApiHttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Subclass ApiServlet and implement {@link #init init} by calling
 * {@link #registerController registerController} with all your controller
 * classes. Example controller class:
 *
 * <pre>
 * public class MyApiController {
 *
 *     &#064;Get("/v1/api/objects")
 *     &#064;JsonBody
 *     public List&lt;SomePojo&gt; listObjects(
 *         &#064;RequestParam("query") Optional&lt;String&gt; query,
 *         &#064;RequestParam("maxHits") Optional&lt;Integer&gt; maxHits
 *     ) {
 *         // ... this is up to you
 *     }
 *
 *     &#064;Get("/v1/api/objects/:id")
 *     &#064;JsonBody
 *     public SomePojo getObject(&#064;PathParam("id") UUID id) {
 *         // ... this is up to you
 *     }
 *
 *     &#064;Post("/v1/api/objects/")
 *     &#064;SendRedirect
 *     public String postData(
 *         &#064;JsonBody SomePojo myPojo,
 *         &#064;SessionParameter("user") Optional&lt;User&gt; user
 *     ) {
 *         // ... do your thing
 *         return "/home/";
 *     }
 * }
 * </pre>
 */
public class ApiServlet extends HttpServlet implements UserContext {

    private static Logger logger = LoggerFactory.getLogger(ApiServlet.class);
    private List<ApiControllerAction> actions = new ArrayList<>();

    public boolean isUserLoggedIn(ApiHttpExchange exchange) {
        return exchange.isUserLoggedIn();
    }

    public boolean isUserInRole(ApiHttpExchange exchange, String role) {
        return exchange.isUserInRole(role);
    }

    private ApiServletCompositeException controllerException;

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            MDC.put("clientIp", req.getRemoteAddr());
            MDC.put("request", req.getContextPath() + req.getServletPath() + req.getPathInfo());

            verifyNoExceptions();
            invokeAction(new ServletHttpExchange(req, resp));
        } finally {
            MDC.clear();
        }
    }

    private void invokeAction(ApiHttpExchange httpExchange) throws IOException {
        for (ApiControllerAction action : actions) {
            if (action.matches(httpExchange)) {
                try {
                    action.invoke(this, httpExchange);
                } catch (HttpActionException e) {
                    e.sendError(httpExchange);
                }
                return;
            }
        }
        logger.warn("No route for {}", httpExchange.getHttpMethod() + " " + httpExchange.getApiURL().getPath() + "[" + httpExchange.getPathInfo() + "]");
        logger.info("Routes {}", actions);
        httpExchange.sendError(404, "No route for " + httpExchange.getHttpMethod() + ": " + httpExchange.getApiURL() + httpExchange.getPathInfo());
    }

    @Override
    public final void init(ServletConfig config) throws ServletException {
        if (config != null) {
            List<String> mappings = config.getServletContext()
                    .getServletRegistrations().values().stream()
                    .flatMap(reg -> reg.getMappings().stream())
                    .collect(Collectors.toList());
            if (mappings.stream().noneMatch(path -> path.endsWith("/*"))) {
                throw new ApiServletException(getClass() + " should have mapping ending with /*, was " + mappings);
            }
        }

        this.controllerException = new ApiServletCompositeException();
        super.init(config);
        verifyNoExceptions();
    }

    protected void verifyNoExceptions() {
        if (!controllerException.isEmpty()) {
            throw controllerException;
        }
    }

    protected void registerController(Object controller) {
        if (controllerException == null) {
            controllerException = new ApiServletCompositeException();
        }
        try {
            this.actions.addAll(ApiControllerMethodAction.registerActions(controller));
        } catch (ApiControllerCompositeException e) {
            controllerException.addControllerException(e);
        }
    }
}

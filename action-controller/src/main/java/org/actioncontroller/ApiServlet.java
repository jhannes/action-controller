package org.actioncontroller;

import org.actioncontroller.json.JsonHttpActionException;
import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.servlet.ServletHttpExchange;
import org.jsonbuddy.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.servlet.ServletConfig;
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
import java.util.Optional;
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

    public boolean isUserLoggedIn(ApiHttpExchange exchange) {
        return exchange.isUserLoggedIn();
    }

    public boolean isUserInRole(ApiHttpExchange exchange, String role) {
        return exchange.isUserInRole(role);
    }

    private Map<String, List<ApiServletAction>> routes = new HashMap<>();
    {
        routes.put("GET", new ArrayList<>());
        routes.put("POST", new ArrayList<>());
        routes.put("PUT", new ArrayList<>());
        routes.put("DELETE", new ArrayList<>());
    }

    private ApiServletCompositeException controllerException;

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            MDC.put("clientIp", req.getRemoteAddr());
            MDC.put("request", req.getContextPath() + req.getServletPath() + req.getPathInfo());

            verifyNoExceptions();
            invokeAction(req, resp);
        } finally {
            MDC.clear();
        }
    }

    protected boolean invokeAction(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        for (ApiServletAction apiRoute : routes.get(req.getMethod())) {
            if (apiRoute.matches(req.getPathInfo())) {
                invoke(createHttpExchange(req, resp, apiRoute), apiRoute);
                return true;
            }
        }

        logger.warn("No route for {} {}[{}]", req.getMethod(), req.getContextPath() + req.getServletPath(), req.getPathInfo());
        new ServletHttpExchange(req, resp, new HashMap<>()).sendError(404, "No route for " + req.getMethod() + ": " + req.getRequestURI());
        return false;
    }

    protected ServletHttpExchange createHttpExchange(HttpServletRequest req, HttpServletResponse resp, ApiServletAction apiRoute) {
        return new ServletHttpExchange(req, resp, apiRoute.collectPathParameters(req.getPathInfo()));
    }

    private void invoke(ApiHttpExchange exchange, ApiServletAction apiRoute) throws IOException {
        try {
            checkPreconditions(exchange, apiRoute.getAction());
            apiRoute.invoke(this, exchange);
        } catch (HttpActionException e) {
            e.sendError(exchange);
        }
    }

    protected void checkPreconditions(ApiHttpExchange req, Method action) {
        verifyUserAccess(req, action);
    }

    // TODO: It feels like there is some more generic concept missing here
    // TODO: Perhaps a mechanism like transaction wrapping could be supported?
    // TODO: Timing logging? MDC boundary?
    protected void verifyUserAccess(ApiHttpExchange exchange, Method action) {
        String role = getRequiredUserRole(action).orElse(null);
        if (role == null) {
            return;
        }
        if (!isUserLoggedIn(exchange)) {
            throw new JsonHttpActionException(401,
                    "User must be logged in for " + action,
                    new JsonObject().put("message", "Login required"));
        }
        if (!isUserInRole(exchange, role)) {
            throw new JsonHttpActionException(403,
                    "User failed to authenticate for " + action + ": Missing role " + role + " for user",
                    new JsonObject().put("message", "Insufficient permissions"));
        }
    }

    protected Optional<String> getRequiredUserRole(Method action) {
        return Optional.ofNullable(
                action.getDeclaredAnnotation(RequireUserRole.class)
        ).map(RequireUserRole::value);
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

    void verifyNoExceptions() {
        if (!controllerException.isEmpty()) {
            throw controllerException;
        }
    }

    protected void registerController(Object controller) {
        if (controllerException == null) {
            controllerException = new ApiServletCompositeException();
        }
        try {
             ApiServletAction.registerActions(controller, routes);
        } catch (ApiControllerCompositeException e) {
            controllerException.addControllerException(e);
        }
    }
}

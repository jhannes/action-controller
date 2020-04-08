package org.actioncontroller.servlet;

import org.actioncontroller.ApiControllerAction;
import org.actioncontroller.ApiControllerCompositeException;
import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.ApiControllerMethodAction;
import org.actioncontroller.ExceptionUtil;
import org.actioncontroller.HttpActionException;
import org.actioncontroller.UserContext;
import org.actioncontroller.jmx.ApiControllerActionMXBeanAdaptor;
import org.actioncontroller.meta.ApiHttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.management.ManagementFactory;
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
    private List<Object> controllers = new ArrayList<>();
    private List<ApiControllerAction> actions = new ArrayList<>();
    private ApiControllerContext context = new ApiControllerContext();

    public ApiServlet() {}

    public ApiServlet(Object controller) {
        controllers.add(controller);
    }

    public ApiControllerContext getContext() {
        return context;
    }

    public boolean isUserLoggedIn(ApiHttpExchange exchange) {
        return exchange.isUserLoggedIn();
    }

    public boolean isUserInRole(ApiHttpExchange exchange, String role) {
        return exchange.isUserInRole(role);
    }

    private ActionControllerConfigurationCompositeException controllerException;

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (
                MDC.MDCCloseable ignored = MDC.putCloseable("clientIp", req.getRemoteAddr());
                MDC.MDCCloseable ignored2 = MDC.putCloseable("request", req.getContextPath() + req.getServletPath() + req.getPathInfo())
        ) {
            verifyNoExceptions();
            invokeAction(new ServletHttpExchange(req, resp));
        }
    }

    private void invokeAction(ApiHttpExchange httpExchange) throws IOException {
        List<ApiControllerAction> candidates = new ArrayList<>();

        for (ApiControllerAction action : actions) {
            if (action.matches(httpExchange)) {
                candidates.add(action);
            }
        }

        if (candidates.size() > 1) {
            candidates = candidates.stream().filter(ApiControllerAction::requiresParameter).collect(Collectors.toList());
        }

        if (candidates.isEmpty()) {
            logger.info("No route for {}", httpExchange.getHttpMethod() + " " + httpExchange.getApiURL().getPath() + "[" + httpExchange.getPathInfo() + "]");
            logger.debug("Routes {}", actions);
            httpExchange.sendError(404, "No route for " + httpExchange.getHttpMethod() + ": " + httpExchange.getApiURL() + httpExchange.getPathInfo());
        } else if (candidates.size() > 1) {
            logger.warn("Ambiguous route for {}", httpExchange.getHttpMethod() + " " + httpExchange.getApiURL().getPath() + "[" + httpExchange.getPathInfo() + "]");
            logger.debug("Routes {}", candidates);
            httpExchange.sendError(404, "No route for " + httpExchange.getHttpMethod() + ": " + httpExchange.getApiURL() + httpExchange.getPathInfo());
        } else {
            try {
                candidates.get(0).invoke(this, httpExchange);
            } catch (HttpActionException e) {
                e.sendError(httpExchange);
            }
        }
    }

    public void registerController(Object controller) {
        this.controllers.add(controller);
    }

    @Override
    public final void init(ServletConfig config) throws ServletException {
        if (!actions.isEmpty()) {
            return;
        }

        if (config != null) {
            List<String> mappings = config.getServletContext()
                    .getServletRegistrations().values().stream()
                    .flatMap(reg -> reg.getMappings().stream())
                    .collect(Collectors.toList());
            if (mappings.stream().noneMatch(path -> path.endsWith("/*"))) {
                throw new ActionControllerConfigurationException(getClass() + " should have mapping ending with /*, was " + mappings);
            }
        }

        this.controllerException = new ActionControllerConfigurationCompositeException();
        super.init(config);
        setupActions();
        verifyNoExceptions();

        if (actions.isEmpty()) {
            throw new ActionControllerConfigurationException(getClass() + " has no controllers. Use ActionServlet(Object) constructor or registerAction() to create create a controller");
        }
    }

    protected void verifyNoExceptions() {
        if (!controllerException.isEmpty()) {
            throw controllerException;
        }
    }

    protected void setupActions() {
        for (Object controller : controllers) {
            if (controllerException == null) {
                controllerException = new ActionControllerConfigurationCompositeException();
            }
            try {
                this.actions.addAll(ApiControllerMethodAction.registerActions(controller, context));
            } catch (ApiControllerCompositeException e) {
                controllerException.addControllerException(e);
            }
        }
    }

    public void registerMBeans() {
        registerMBeans(ManagementFactory.getPlatformMBeanServer());
    }

    public void registerMBeans(MBeanServer mBeanServer) {
        try {
            for (ApiControllerAction action : actions) {
                mBeanServer.registerMBean(
                        new ApiControllerActionMXBeanAdaptor(action),
                        new ObjectName("org.actioncontroller:controller=" + action.getController().getClass().getName() + ",action=" + action.getAction().getName())
                );
            }
        } catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException | MalformedObjectNameException e) {
            throw ExceptionUtil.softenException(e);
        }

    }
}

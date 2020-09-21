package org.actioncontroller.servlet;

import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.TimerRegistry;
import org.actioncontroller.UserContext;
import org.actioncontroller.meta.ApiHttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.management.MBeanServer;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
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

    private static final Logger logger = LoggerFactory.getLogger(ApiServlet.class);
    private final List<Object> controllers = new ArrayList<>();
    private final ApiControllerActionRouter actions = new ApiControllerActionRouter();
    private final ApiControllerContext context = new ApiControllerContext();
    private TimerRegistry timerRegistery = TimerRegistry.NULL;

    public ApiServlet() {}

    public ApiServlet(Object controller) {
        registerController(controller);
    }

    public ApiControllerContext getContext() {
        return context;
    }

    @Override
    public boolean isUserLoggedIn(ApiHttpExchange exchange) {
        return exchange.isUserLoggedIn();
    }

    @Override
    public boolean isUserInRole(ApiHttpExchange exchange, String role) {
        return exchange.isUserInRole(role);
    }

    private ActionControllerConfigurationCompositeException controllerException;

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (
                MDC.MDCCloseable ignored = MDC.putCloseable("clientIp", req.getRemoteAddr());
                MDC.MDCCloseable ignored2 = MDC.putCloseable("requestPath", req.getContextPath() + req.getServletPath() + req.getPathInfo())
        ) {
            controllerException.verifyNoExceptions();
            invokeAction(new ServletHttpExchange(req, resp));
        }
    }

    private void invokeAction(ApiHttpExchange httpExchange) throws IOException {
        actions.invokeAction(httpExchange, this);
    }

    public void registerController(Object controller) {
        if (controller instanceof Collection) {
            controllers.addAll((Collection<?>)controller);
        } else {
            controllers.add(controller);
        }
    }

    public void registerControllers(Object... controllers) {
        registerControllerList(List.of(controllers));
    }

    public void registerControllerList(List<Object> controllers) {
        for (Object controller : controllers) {
            registerController(controller);
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
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
        setupActions();
        controllerException.verifyNoExceptions();

        if (actions.isEmpty()) {
            throw new ActionControllerConfigurationException(getClass() + " has no controllers. Use ActionServlet(Object) constructor or registerAction() to create create a controller");
        }
        super.init(config);
    }

    protected void setupActions() {
        actions.setupActions(controllers, context, controllerException);
    }

    public void registerMBeans() {
        registerMBeans(ManagementFactory.getPlatformMBeanServer());
    }

    public void registerMBeans(MBeanServer mBeanServer) {
        actions.registerMBeans(mBeanServer);
    }

    @Override
    public TimerRegistry getTimerRegistry() {
        return timerRegistery;
    }

    public void setTimerRegistry(TimerRegistry timerRegistry) {
        this.timerRegistery = timerRegistry;
    }
}

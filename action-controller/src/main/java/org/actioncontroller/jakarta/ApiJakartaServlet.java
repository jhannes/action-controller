package org.actioncontroller.jakarta;

import org.actioncontroller.ApiControllerActionRouter;
import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.TimerRegistry;
import org.actioncontroller.UserContext;
import org.actioncontroller.ApiHttpExchange;
import org.actioncontroller.exceptions.ActionControllerConfigurationCompositeException;
import org.actioncontroller.exceptions.ActionControllerConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.management.MBeanServer;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ApiJakartaServlet extends HttpServlet implements UserContext {

    private static final Logger logger = LoggerFactory.getLogger(ApiJakartaServlet.class);
    private final List<Object> controllers = new ArrayList<>();
    private final ApiControllerActionRouter actions = new ApiControllerActionRouter();
    private final ApiControllerContext context = new ApiControllerContext();
    private TimerRegistry timerRegistery = TimerRegistry.NULL;

    public ApiJakartaServlet() {}

    public ApiJakartaServlet(Object controller) {
        registerController(controller);
    }

    public ApiControllerContext getContext() {
        return context;
    }

    private ActionControllerConfigurationCompositeException controllerException;

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (
                MDC.MDCCloseable ignored = MDC.putCloseable("clientIp", req.getRemoteAddr());
                MDC.MDCCloseable ignored2 = MDC.putCloseable("requestPath", req.getContextPath() + req.getServletPath() + req.getPathInfo())
        ) {
            controllerException.verifyNoExceptions();
            invokeAction(new JakartaServletHttpExchange(req, resp));
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

    /**
     * This method lets you plug in metrics for measuring which actions are executed and how
     * long they take. See {@link TimerRegistry}
     */
    public void setTimerRegistry(TimerRegistry timerRegistry) {
        this.timerRegistery = timerRegistry;
    }
}

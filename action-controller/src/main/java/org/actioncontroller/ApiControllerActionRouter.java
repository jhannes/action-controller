package org.actioncontroller;

import org.actioncontroller.exceptions.ActionControllerConfigurationCompositeException;
import org.actioncontroller.exceptions.ActionControllerConfigurationException;
import org.actioncontroller.exceptions.ApiControllerCompositeException;
import org.actioncontroller.exceptions.HttpActionException;
import org.actioncontroller.exceptions.HttpNotFoundException;
import org.actioncontroller.jmx.ApiControllerActionMXBeanAdaptor;
import org.actioncontroller.util.ExceptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.actioncontroller.meta.ApiControllerActionFactory.createNewInstance;

/**
 * A collection of ApiControllerActions which allows for routing
 */
public class ApiControllerActionRouter {
    private static final Logger logger = LoggerFactory.getLogger(ApiControllerActionRouter.class);

    private final Map<String, ApiControllerRouteMap> rootRoutes = new HashMap<>();
    private final List<ApiControllerAction> actions = new ArrayList<>();

    public void invokeAction(ApiHttpExchange httpExchange, UserContext userContext) throws IOException {
        try {
            ApiControllerAction action = findAction(httpExchange);
            long startTime = System.currentTimeMillis();
            action.invoke(userContext, httpExchange);
            long executionTime = System.currentTimeMillis() - startTime;
            userContext.getTimerRegistry().getTimer(action).update(Duration.ofMillis(executionTime));
        } catch (HttpActionException e) {
            e.sendError(httpExchange);
        }
    }

    public ApiControllerAction findAction(ApiHttpExchange exchange) {
        ApiControllerRouteMap routeMap = rootRoutes.get(exchange.getHttpMethod());
        if (routeMap == null) {
            logger.info("Unhandled method {}. Routes {}",  "[" + exchange.getHttpMethod() + "] " + exchange.getApiURL(), rootRoutes.keySet());
            throw new HttpNotFoundException("No route for " + exchange.getHttpMethod() + ": " + exchange.getApiURL() + exchange.getPathInfo());
        }
        return routeMap.findAction(exchange.getPathInfo().split("/"), 0, exchange);
    }

    public boolean isEmpty() {
        return rootRoutes.values().stream().allMatch(ApiControllerRouteMap::isEmpty);
    }


    public void addController(Object controller, ApiControllerContext context) {
        for (ApiControllerAction action : createActions(controller, context)) {
            add(action);
        }
    }

    public void add(ApiControllerAction action) {
        logger.info("Installing route {}", action);
        actions.add(action);
        rootRoutes.computeIfAbsent(action.getHttpMethod(), method -> new ApiControllerRouteMap())
                .add(action, 0);
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

    public void setupActions(List<Object> controllers, ApiControllerContext context, ActionControllerConfigurationCompositeException controllerException) {
        for (Object controller : controllers) {
            if (controllerException == null) {
                controllerException = new ActionControllerConfigurationCompositeException();
            }
            try {
                addController(controller, context);
            } catch (ApiControllerCompositeException e) {
                controllerException.addControllerException(e);
            }
        }
    }

    public static List<ApiControllerAction> createActions(Object controller, ApiControllerContext context) {
        List<ApiControllerAction> actions = new ArrayList<>();
        ApiControllerCompositeException exceptions = new ApiControllerCompositeException(controller);
        for (Method method : controller.getClass().getMethods()) {
            try {
                createNewInstance(controller, context, method).ifPresent(actions::add);
            } catch (ActionControllerConfigurationException e) {
                logger.error("Failed to setup {}", getMethodName(method), e);
                exceptions.addActionException(e);
            } catch (Exception e) {
                logger.error("Failed to setup {}", getMethodName(method), e);
                exceptions.addActionException(new ActionControllerConfigurationException("Failed to set up " + getMethodName(method), e));
            }
        }
        if (!exceptions.isEmpty()) {
            throw exceptions;
        }
        if (actions.isEmpty()) {
            throw new ActionControllerConfigurationException("Controller has no actions: " + controller);
        }
        return actions;
    }

    private static Object getMethodName(Method action) {
        String parameters = Stream.of(action.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(","));
        return action.getDeclaringClass().getSimpleName() + "." + action.getName() + "(" + parameters + ")";
    }
}

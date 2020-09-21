package org.actioncontroller.servlet;

import org.actioncontroller.ApiControllerAction;
import org.actioncontroller.ApiControllerCompositeException;
import org.actioncontroller.ApiControllerContext;
import org.actioncontroller.ExceptionUtil;
import org.actioncontroller.UserContext;
import org.actioncontroller.jmx.ApiControllerActionMXBeanAdaptor;
import org.actioncontroller.meta.ApiHttpExchange;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.actioncontroller.meta.ApiControllerActionFactory.createNewInstance;

/**
 * A collection of ApiControllerActions which allows for routing
 */
public class ApiControllerActionRouter {
    private static final Logger logger = LoggerFactory.getLogger(ApiControllerActionRouter.class);

    private final List<ApiControllerAction> actions = new ArrayList<>();

    public void invokeAction(ApiHttpExchange httpExchange, UserContext userContext) throws IOException {
        Optional<ApiControllerAction> action = findAction(httpExchange);
        if (action.isPresent()) {
            long startTime = System.currentTimeMillis();
            action.get().invoke(userContext, httpExchange);
            long executionTime = System.currentTimeMillis() - startTime;
            userContext.getTimerRegistry().getTimer(action.get()).update(Duration.ofMillis(executionTime));
        }
    }

    public Optional<ApiControllerAction> findAction(ApiHttpExchange httpExchange) throws IOException {
        List<ApiControllerAction> candidates = new ArrayList<>();

        for (ApiControllerAction action : actions) {
            if (action.matches(httpExchange)) {
                candidates.add(action);
            }
        }

        if (candidates.size() > 1) {
            List<ApiControllerAction> filteredCandidates = candidates.stream().filter(ApiControllerAction::requiresParameter).collect(Collectors.toList());
            if (filteredCandidates.isEmpty()) {
                logger.warn("Ambiguous route for {}", httpExchange.getHttpMethod() + " " + httpExchange.getApiURL().getPath() + "[" + httpExchange.getPathInfo() + "]");
                logger.debug("Routes {}", candidates);
                httpExchange.sendError(404, "No route for " + httpExchange.getHttpMethod() + ": " + httpExchange.getApiURL() + httpExchange.getPathInfo());
                return Optional.empty();
            }
            candidates = filteredCandidates;
        }

        if (candidates.isEmpty()) {
            logger.info("No route for {}", httpExchange.getHttpMethod() + " " + httpExchange.getApiURL().getPath() + "[" + httpExchange.getPathInfo() + "]");
            logger.debug("Routes {}", actions);
            httpExchange.sendError(404, "No route for " + httpExchange.getHttpMethod() + ": " + httpExchange.getApiURL() + httpExchange.getPathInfo());
            return Optional.empty();
        } else if (candidates.size() > 1) {
            logger.warn("Ambiguous route for {}", httpExchange.getHttpMethod() + " " + httpExchange.getApiURL().getPath() + "[" + httpExchange.getPathInfo() + "]");
            logger.debug("Routes {}", candidates);
            httpExchange.sendError(404, "No route for " + httpExchange.getHttpMethod() + ": " + httpExchange.getApiURL() + httpExchange.getPathInfo());
            return Optional.empty();
        }

        return Optional.of(candidates.get(0));
    }

    public boolean isEmpty() {
        return actions.isEmpty();
    }

    public void add(ApiControllerAction action) {
        for (ApiControllerAction existingAction : actions) {
            if (existingAction.matches(action)) {
                throw new ActionControllerConfigurationException(action + " is in conflict with " + existingAction);
            }
        }
        actions.add(action);
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
                List<ApiControllerAction> actions = createActions(controller, context);
                for (ApiControllerAction action : actions) {
                    add(action);
                }
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
                createNewInstance(controller, context, method).ifPresent(action -> {
                    logger.info("Installing route {}", action);
                    actions.add(action);
                });
            } catch (ActionControllerConfigurationException e) {
                logger.warn("Failed to setup {}", getMethodName(method), e);
                exceptions.addActionException(e);
            } catch (Exception e) {
                logger.warn("Failed to setup {}", getMethodName(method), e);
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

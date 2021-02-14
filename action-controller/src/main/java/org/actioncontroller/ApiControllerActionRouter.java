package org.actioncontroller;

import org.actioncontroller.jmx.ApiControllerActionMXBeanAdaptor;
import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.servlet.ActionControllerConfigurationCompositeException;
import org.actioncontroller.servlet.ActionControllerConfigurationException;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.actioncontroller.meta.ApiControllerActionFactory.createNewInstance;

/**
 * A collection of ApiControllerActions which allows for routing
 */
public class ApiControllerActionRouter {
    private static final Logger logger = LoggerFactory.getLogger(ApiControllerActionRouter.class);
    
    private static class RouteMap {
        private final Map<String, RouteMap> subRoutes = new HashMap<>();
        private final Map<String, Set<ApiControllerAction>> actions = new HashMap<>();
        private final Map<Pattern, RouteMap> patternSubRoutes = new HashMap<>();
        private final Map<Pattern, ApiControllerAction> patternActions = new HashMap<>();

        public ApiControllerAction findAction(String[] pathParts, int index, ApiHttpExchange exchange) {
            if (pathParts.length == 0) {
                return findLeafAction(exchange, "");
            } else if (index == pathParts.length-1) {
                return findLeafAction(exchange, pathParts[index]);
            } else if (subRoutes.containsKey(pathParts[index])) {
                return subRoutes.get(pathParts[index]).findAction(pathParts, index+1, exchange);
            } else {
                for (Map.Entry<Pattern, RouteMap> entry : patternSubRoutes.entrySet()) {
                    if (entry.getKey().matcher(pathParts[index]).matches()) {
                        return entry.getValue().findAction(pathParts, index+1, exchange);
                    }
                }
                logger.info("No route for {}. Routes {}",  exchange.getHttpMethod() + " " + exchange.getApiURL().getPath() + "[" + exchange.getPathInfo() + "]", actions);
                throw new HttpNotFoundException("No route for " + exchange.getHttpMethod() + ": " + exchange.getApiURL() + exchange.getPathInfo());
            }
        }
        
        private ApiControllerAction findLeafAction(ApiHttpExchange exchange, String pathPart) {
            if (actions.containsKey(pathPart)) {
                Set<ApiControllerAction> actions = this.actions.get(pathPart)
                        .stream().filter(a -> a.matchesRequiredParameters(exchange))
                        .collect(Collectors.toSet());
                if (actions.size() > 1) {
                    Set<ApiControllerAction> filteredCandidates = actions.stream().filter(ApiControllerAction::requiresParameter).collect(Collectors.toSet());
                    if (filteredCandidates.size() == 1) {
                        actions = filteredCandidates;
                    }
                }
                if (actions.size() > 1) {
                    logger.info("Multiple routes for {}. Routes {}",  exchange.getHttpMethod() + " " + exchange.getApiURL().getPath() + "[" + exchange.getPathInfo() + "]", actions);
                    throw new HttpRequestException("More than one matching");
                } else if (actions.isEmpty()) {
                    logger.info("No route for {}. Routes {}",  exchange.getHttpMethod() + " " + exchange.getApiURL().getPath() + "[" + exchange.getPathInfo() + "]", actions);
                    throw new HttpNotFoundException("No route for " + exchange.getHttpMethod() + ": " + exchange.getApiURL() + exchange.getPathInfo());
                }
                return actions.iterator().next();
            } else {
                for (Map.Entry<Pattern, ApiControllerAction> entry : patternActions.entrySet()) {
                    if (entry.getKey().matcher(pathPart).matches()) {
                        return entry.getValue();
                    }
                }
                logger.info("No route for {}. Routes {}",  exchange.getHttpMethod() + " " + exchange.getApiURL().getPath() + "[" + exchange.getPathInfo() + "]", actions);
                throw new HttpNotFoundException("No route for " + exchange.getHttpMethod() + ": " + exchange.getApiURL() + exchange.getPathInfo());
            }
        }        
        
        public void add(ApiControllerAction action, int index) {
            String[] pathParts = action.getPatternParts();

            if (pathParts.length == 0) {
                actions.computeIfAbsent("", key -> new HashSet<>())
                        .add(action);
            } else if (index == pathParts.length - 1) {
                if (action.getParamRegexp()[index] != null) {
                    for (Map.Entry<Pattern, ApiControllerAction> existingEntry : patternActions.entrySet()) {
                        if (existingEntry.getKey().pattern().equals(action.getParamRegexp()[index].pattern())) {
                            throw new ActionControllerConfigurationException(action + " is in conflict with " + existingEntry.getValue());
                        }
                    }
                    patternActions.put(action.getParamRegexp()[index], action);
                } else {
                    Set<ApiControllerAction> existingActions = actions.computeIfAbsent(pathParts[index], key -> new HashSet<>());
                    for (ApiControllerAction existingAction : existingActions) {
                        if (existingAction.matches(action)) {
                            throw new ActionControllerConfigurationException(action + " is in conflict with " + existingAction);
                        }
                    }
                    existingActions.add(action);
                }
            } else {
                if (action.getParamRegexp()[index] != null) {
                    patternSubRoutes.computeIfAbsent(action.getParamRegexp()[index], key -> new RouteMap())
                            .add(action, index+1);
                } else {
                    subRoutes.computeIfAbsent(pathParts[index], key -> new RouteMap())
                            .add(action, index+1);
                }
            }
        }

        public boolean isEmpty() {
            return subRoutes.isEmpty() && patternSubRoutes.isEmpty() && actions.isEmpty() && patternActions.isEmpty();
        }
    }
    
    private final Map<String, RouteMap> rootRoutes = new HashMap<>();
    private final List<ApiControllerAction> actions = new ArrayList<>();

    public ApiControllerActionRouter() {
        rootRoutes.put("GET", new RouteMap());
        rootRoutes.put("POST", new RouteMap());
        rootRoutes.put("PUT", new RouteMap());
        rootRoutes.put("DELETE", new RouteMap());
    }

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

    public ApiControllerAction findAction(ApiHttpExchange httpExchange) {
        return rootRoutes.get(httpExchange.getHttpMethod())
                .findAction(httpExchange.getPathInfo().split("/"), 0, httpExchange);
    }

    public boolean isEmpty() {
        return rootRoutes.values().stream().allMatch(RouteMap::isEmpty);
    }


    public void addController(Object controller, ApiControllerContext context) {
        for (ApiControllerAction action : createActions(controller, context)) {
            add(action);
        }
    }

    public void add(ApiControllerAction action) {
        logger.info("Installing route {}", action);
        actions.add(action);
        rootRoutes.get(action.getHttpMethod()).add(action, 0);
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

package org.actioncontroller;

import org.actioncontroller.meta.ApiHttpExchange;
import org.actioncontroller.servlet.ActionControllerConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ApiControllerRouteMap {
    private static final Logger logger = LoggerFactory.getLogger(ApiControllerRouteMap.class);

    private final Map<String, ApiControllerRouteMap> subRoutes = new HashMap<>();
    private final Map<String, Set<ApiControllerAction>> actions = new HashMap<>();
    private final Map<Pattern, ApiControllerRouteMap> patternSubRoutes = new HashMap<>();
    private final Map<Pattern, ApiControllerAction> patternActions = new HashMap<>();

    void add(ApiControllerAction action, int index) {
        String[] pathParts = action.getPatternParts();

        if (pathParts.length == 0) {
            addPathConstantAction(action, "");
        } else if (lastPart(pathParts, index)) {
            addAction(action, index, pathParts);
        } else {
            addSubRoute(action, index, pathParts);
        }
    }

    private void addPathConstantAction(ApiControllerAction action, String pathPart) {
        Set<ApiControllerAction> existingActions = actions.computeIfAbsent(pathPart, key -> new HashSet<>());
        for (ApiControllerAction existingAction : existingActions) {
            if (existingAction.matches(action)) {
                throw new ActionControllerConfigurationException(action + " is in conflict with " + existingAction);
            }
        }
        existingActions.add(action);
    }

    private void addAction(ApiControllerAction action, int index, String[] pathParts) {
        Pattern paramPattern = action.getParamRegexp()[index];
        if (paramPattern != null) {
            addPathPatternAction(action, paramPattern);
        } else {
            addPathConstantAction(action, pathParts[index]);
        }
    }

    private void addSubRoute(ApiControllerAction action, int index, String[] pathParts) {
        Pattern paramPattern = action.getParamRegexp()[index];
        if (paramPattern != null) {
            for (Map.Entry<Pattern, ApiControllerRouteMap> entry : patternSubRoutes.entrySet()) {
                if (entry.getKey().pattern().equals(paramPattern.pattern())) {
                    entry.getValue().add(action, index+1);
                    return;
                }
            }
            ApiControllerRouteMap routeMap = new ApiControllerRouteMap();
            routeMap.add(action, index+1);
            patternSubRoutes.put(paramPattern, routeMap);
        } else {
            subRoutes.computeIfAbsent(pathParts[index], k -> new ApiControllerRouteMap())
                    .add(action, index + 1);
        }
    }

    private void addPathPatternAction(ApiControllerAction action, Pattern paramPattern) {
        for (Map.Entry<Pattern, ApiControllerAction> existingEntry : patternActions.entrySet()) {
            if (existingEntry.getKey().pattern().equals(paramPattern.pattern())) {
                throw new ActionControllerConfigurationException(action + " is in conflict with " + existingEntry.getValue());
            }
        }
        patternActions.put(paramPattern, action);
    }

    ApiControllerAction findAction(String[] pathParts, int index, ApiHttpExchange exchange) {
        if (pathParts.length == 0) {
            return findLeafAction(exchange, "");
        }

        if (lastPart(pathParts, index)) {
            return findLeafAction(exchange, pathParts[index]);
        }

        return actionSubRoute(pathParts[index])
            .map(subroute -> subroute.findAction(pathParts, index + 1, exchange))
            .orElseThrow(() -> {
                logger.info("No route for {}. Routes {}",  exchange.getHttpMethod() + " " + exchange.getApiURL().getPath() + "[" + exchange.getPathInfo() + "]", actions);
                throw new HttpNotFoundException("No route for " + exchange.getHttpMethod() + ": " + exchange.getApiURL() + exchange.getPathInfo());
            });
    }

    private Optional<ApiControllerRouteMap> actionSubRoute(String pathPart){
        return subRoutes.containsKey(pathPart) ?
            Optional.ofNullable(subRoutes.get(pathPart)) :
            findMatch(patternSubRoutes, pathPart);
    }

    private ApiControllerAction findLeafAction(ApiHttpExchange exchange, String pathPart) {
        if (actions.containsKey(pathPart)) {
            Set<ApiControllerAction> matchingActions = this.actions.get(pathPart).stream()
                    .filter(a -> a.matchesRequiredParameters(exchange))
                    .collect(Collectors.toSet());
            return findUnambiguousAction(exchange, matchingActions);
        }

        return findMatch(patternActions, pathPart).orElseThrow(() -> {
            logger.info("No route for {}. Routes {}",  exchange.getHttpMethod() + " " + exchange.getApiURL().getPath() + "[" + exchange.getPathInfo() + "]", actions);
            return new HttpNotFoundException("No route for " + exchange.getHttpMethod() + ": " + exchange.getApiURL() + exchange.getPathInfo());
        });
    }

    private ApiControllerAction findUnambiguousAction(ApiHttpExchange exchange, Set<ApiControllerAction> actions) {
        Set<ApiControllerAction> filteredActions = actions.size() <= 1 ? actions : onlyWithRequiredParameters(actions);
        if (filteredActions.size() == 1) {
            return filteredActions.iterator().next();
        }

        if (filteredActions.size() > 1) {
            logger.info("Multiple routes for {}. Routes {}",  exchange.getHttpMethod() + " " + exchange.getApiURL().getPath() + "[" + exchange.getPathInfo() + "]", filteredActions);
            throw new HttpRequestException("More than one matching");
        } else {
            logger.info("No route for {}. Routes {}",  exchange.getHttpMethod() + " " + exchange.getApiURL().getPath() + "[" + exchange.getPathInfo() + "]", filteredActions);
            throw new HttpNotFoundException("No route for " + exchange.getHttpMethod() + ": " + exchange.getApiURL() + exchange.getPathInfo());
        }
    }

    private static Set<ApiControllerAction> onlyWithRequiredParameters(Set<ApiControllerAction> actions) {
        return actions.stream()
                .filter(ApiControllerAction::requiresParameter)
                .collect(Collectors.toSet());
    }

    private static <T> Optional<T> findMatch(Map<Pattern, T> map, String value) {
        for (Map.Entry<Pattern, T> entry : map.entrySet()) {
            if (entry.getKey().matcher(value).matches()) {
                return Optional.ofNullable(entry.getValue());
            }
        }
        return Optional.empty();
    }

    public boolean isEmpty() {
        return subRoutes.isEmpty() && patternSubRoutes.isEmpty() && actions.isEmpty() && patternActions.isEmpty();
    }

    private boolean lastPart(String[] pathParts, int index) {
        return pathParts.length - 1 == index;
    }

}

package org.actioncontroller.exceptions;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Collects all exceptions during configuration of a specific controller
 */
public class ApiControllerCompositeException extends ActionControllerConfigurationException {

    private final List<Exception> exceptions = new ArrayList<>();
    private final Object controller;

    public ApiControllerCompositeException(Object controller) {
        super(null);
        this.controller = controller;
    }

    public void addActionException(Exception exception) {
        exceptions.add(exception);
    }

    public Object getController() {
        return controller;
    }

    @Override
    public String getMessage() {
        return "Could not create routes for controller of " + controller.getClass() + ":\n"
                + exceptions.stream().map(Throwable::getMessage).collect(Collectors.joining("\t\n"));
    }

    public boolean isEmpty() {
        return exceptions.isEmpty();
    }

}

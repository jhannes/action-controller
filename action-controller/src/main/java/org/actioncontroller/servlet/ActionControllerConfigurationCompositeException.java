package org.actioncontroller.servlet;

import org.actioncontroller.ApiControllerCompositeException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Collects all exceptions during configuration to give a total view of all
 * configuration exceptions that may have occurred.
 */
public class ActionControllerConfigurationCompositeException extends ActionControllerConfigurationException {

    private List<ApiControllerCompositeException> exceptions = new ArrayList<>();

    public ActionControllerConfigurationCompositeException() {
        super(null);
    }

    public void addControllerException(ApiControllerCompositeException exception) {
        exceptions.add(exception);
    }

    @Override
    public String getMessage() {
        return "Failed to start because of problems with controllers: "
                + exceptions.stream().map(e -> e.getController().getClass().getName()).collect(Collectors.joining(", ")) + ":\n"
                + exceptions.stream().map(ApiControllerCompositeException::getMessage).collect(Collectors.joining("\t\n"));
    }

    public boolean isEmpty() {
        return exceptions.isEmpty();
    }
}

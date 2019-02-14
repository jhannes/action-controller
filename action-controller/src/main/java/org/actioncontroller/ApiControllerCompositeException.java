package org.actioncontroller;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ApiControllerCompositeException extends ApiServletException {

    private List<ApiServletException> exceptions = new ArrayList<>();
    private Object controller;

    public ApiControllerCompositeException(Object controller) {
        super(null);
        this.controller = controller;
    }

    public void addActionException(ApiServletException exception) {
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

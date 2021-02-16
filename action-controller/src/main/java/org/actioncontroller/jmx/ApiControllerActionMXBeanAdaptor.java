package org.actioncontroller.jmx;

import org.actioncontroller.ApiControllerAction;

public class ApiControllerActionMXBeanAdaptor implements ApiControllerActionMXBean {
    private ApiControllerAction action;

    public ApiControllerActionMXBeanAdaptor(ApiControllerAction action) {
        this.action = action;
    }

    @Override
    public String getPath() {
        return action.getPattern();
    }

    @Override
    public String getHttpMethod() {
        return action.getHttpMethod();
    }

}

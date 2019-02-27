package org.actioncontroller.meta;

public @interface ActionControllerFilter {

    Class<? extends FilterHandler> value();

}

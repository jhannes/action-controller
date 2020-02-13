package org.actioncontroller.jmx;

public interface ApiControllerActionMXBean {
    String getPath();

    String getHttpMethod();

    boolean testPath(String path);
}

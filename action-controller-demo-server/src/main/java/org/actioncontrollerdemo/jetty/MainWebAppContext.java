package org.actioncontrollerdemo.jetty;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.resource.Resource;

import javax.servlet.ServletContextListener;
import java.io.File;

public class MainWebAppContext extends ServletContextHandler {
    public MainWebAppContext(String contextPath, String baseResource, ServletContextListener... listeners) {
        setContextPath(contextPath);

        Resource base = Resource.newClassPathResource(baseResource);
        File resourceSrc = new File(base.getURI().getPath().replace("/target/classes/", "/src/main/resources/"));
        if (resourceSrc.exists()) {
            setBaseResource(Resource.newResource(resourceSrc));
            this.getInitParams().put("org.eclipse.jetty.servlet.Default.useFileMappedBuffer", "false");
        } else {
            setBaseResource(base);
        }

        for (ServletContextListener listener : listeners) {
            addEventListener(listener);
        }
    }
}

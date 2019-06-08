package org.actioncontrollerdemo;

import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;

import javax.servlet.ServletContextListener;
import java.io.File;

public class MainWebAppContext extends WebAppContext {
    public MainWebAppContext(String contextPath, String baseResource, ServletContextListener... listeners) {
        setContextPath(contextPath);

        File resourceSrc = new File("src/main/resources/" + baseResource);
        if (resourceSrc.exists()) {
            setBaseResource(Resource.newResource(resourceSrc));
            this.getInitParams().put("org.eclipse.jetty.servlet.Default.useFileMappedBuffer", "false");
        } else {
            setBaseResource(Resource.newClassPathResource(baseResource));
        }

        for (ServletContextListener listener : listeners) {
            addEventListener(listener);
        }
    }
}

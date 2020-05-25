package org.actioncontrollerdemo.servlet;

import org.actioncontroller.servlet.ApiServlet;
import org.actioncontrollerdemo.TestController;
import org.actioncontrollerdemo.UserController;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.util.EnumSet;

public class DemoListener implements ServletContextListener {
    private final Filter principalFilter;
    private Runnable updater;

    public DemoListener(Runnable updater, Filter principalFilter) {
        this.updater = updater;
        this.principalFilter = principalFilter;
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();
        ApiServlet apiServlet = new ApiServlet(new TestController(updater));
        apiServlet.registerController(new UserController());
        context.addServlet("api", apiServlet).addMapping("/api/*");
        context.addServlet("swagger", new WebJarServlet("swagger-ui"))
                .addMapping("/swagger/*");
        context.addServlet("default", new ContentServlet("/webapp-actioncontrollerdemo/"))
                .addMapping("/*");
        context.addFilter("secureConnectionFilter", new SecureConnectionFilter())
                .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, "*");
        context.addFilter("principalFilter", principalFilter)
                .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST),  false, "*");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {

    }
}

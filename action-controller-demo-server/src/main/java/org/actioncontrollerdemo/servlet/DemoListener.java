package org.actioncontrollerdemo.servlet;

import org.actioncontroller.ExceptionUtil;
import org.actioncontroller.servlet.ApiServlet;
import org.actioncontrollerdemo.TestController;

import javax.management.JMException;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.lang.management.ManagementFactory;

public class DemoListener implements ServletContextListener {
    private Runnable updater;

    public DemoListener(Runnable updater) {
        this.updater = updater;
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();
        ApiServlet apiServlet = new ApiServlet(new TestController(updater)) {
            @Override
            protected void setupActions() {
                super.setupActions();
                try {
                    registerMBeans(ManagementFactory.getPlatformMBeanServer());
                } catch (JMException e) {
                    throw ExceptionUtil.softenException(e);
                }
            }
        };
        context.addServlet("api", apiServlet).addMapping("/api/*");
        context.addServlet("swagger", new WebJarServlet("swagger-ui"))
                .addMapping("/swagger/*");
        context.addServlet("default", new ContentServlet()).addMapping("/*");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {

    }
}

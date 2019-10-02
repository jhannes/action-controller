package org.actioncontrollerdemo.servlet;

import org.actioncontroller.servlet.ApiServlet;
import org.actioncontrollerdemo.TestController;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class DemoListener implements ServletContextListener {
    private Runnable updater;

    public DemoListener(Runnable updater) {
        this.updater = updater;
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();
        context.addServlet("api", new ApiServlet(new TestController(updater))).addMapping("/api/*");
        context.addServlet("swagger", new WebJarServlet("swagger-ui"))
                .addMapping("/swagger/*");
        context.addServlet("default", new ContentServlet()).addMapping("/*");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {

    }
}

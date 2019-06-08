package org.actioncontrollerdemo;

import org.actioncontroller.ApiServlet;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class DemoListener implements ServletContextListener {
    private Runnable updater;

    public DemoListener(Runnable updater) {
        this.updater = updater;
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        sce.getServletContext().addServlet("api", new ApiServlet() {
            @Override
            public void init() {
                registerController(new TestController(updater));
            }
        }).addMapping("/api/*");
        sce.getServletContext().addServlet("swagger", new WebJarServlet("swagger-ui"))
                .addMapping("/swagger/*");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {

    }
}

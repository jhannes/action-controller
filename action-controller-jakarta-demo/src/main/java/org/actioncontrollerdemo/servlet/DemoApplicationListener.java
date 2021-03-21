package org.actioncontrollerdemo.servlet;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.health.HealthCheckRegistry;
import io.dropwizard.metrics.servlets.AdminServlet;
import io.dropwizard.metrics.servlets.HealthCheckServlet;
import io.dropwizard.metrics.servlets.MetricsServlet;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import org.actioncontroller.TimerRegistry;
import org.actioncontroller.jakarta.ApiJakartaServlet;
import org.actioncontroller.servlet.ApiServlet;
import org.actioncontrollerdemo.TestController;
import org.actioncontrollerdemo.UserController;

import java.util.EnumSet;

public class DemoApplicationListener implements ServletContextListener {
    private final Filter principalFilter;
    private Runnable updater;
    private MetricRegistry metricRegistry = new MetricRegistry();
    private HealthCheckRegistry healthCheckRegistry = new HealthCheckRegistry();

    public DemoApplicationListener(Runnable updater, Filter principalFilter) {
        this.updater = updater;
        this.principalFilter = principalFilter;
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();
        context.setAttribute(MetricsServlet.METRICS_REGISTRY, metricRegistry);
        context.setAttribute(HealthCheckServlet.HEALTH_CHECK_REGISTRY, healthCheckRegistry);

        ApiJakartaServlet apiServlet = new ApiJakartaServlet(new TestController(updater));
        TimerRegistry counterRegistry = action -> {
            String name = action.getController().getClass().getSimpleName() + "/" + action.getMethodName();
            Timer histogram = metricRegistry.timer(name);
            return histogram::update;
        };
        apiServlet.setTimerRegistry(counterRegistry);
        apiServlet.registerController(new UserController());
        context.addServlet("api", apiServlet).addMapping("/api/*");
        context.addServlet("dropwizard", new AdminServlet()).addMapping("/status/*");
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

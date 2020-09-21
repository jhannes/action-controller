package org.actioncontrollerdemo.servlet;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.servlets.AdminServlet;
import com.codahale.metrics.servlets.HealthCheckServlet;
import com.codahale.metrics.servlets.MetricsServlet;
import org.actioncontroller.ApiControllerAction;
import org.actioncontroller.TimerRegistry;
import org.actioncontroller.servlet.ApiServlet;
import org.actioncontrollerdemo.TestController;
import org.actioncontrollerdemo.UserController;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.time.Duration;
import java.util.EnumSet;
import java.util.function.Consumer;
import java.util.function.Function;

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

        ApiServlet apiServlet = new ApiServlet(new TestController(updater));
        TimerRegistry counterRegistry = action -> {
            String name = ApiServlet.class.getSimpleName() + "/" + action.getMethodName();
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
